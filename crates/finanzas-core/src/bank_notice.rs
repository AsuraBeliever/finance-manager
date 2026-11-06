//! Bank notice reader: turns the text of a bank's movement notification (the
//! email a bank sends after a card purchase, a SPEI transfer or a deposit)
//! into a structured movement — kind, amount in cents, merchant, card ending,
//! date and time — or rejects it (promotion, statement, declined charge,
//! truncated text).
//!
//! Two stages, both pure and allocation-light (the worker runs this inside a
//! ~10 ms CPU budget, so no regex engine):
//!
//! 1. `tokenize` — normalized text (lowercase, accents folded) → tokens:
//!    words, money amounts, digit runs, masked card numbers, dates, times and
//!    separators. Each word keeps its original spelling for the merchant name.
//! 2. `Transducer` — a Mealy machine over that token stream: each (state,
//!    token) pair moves to a next state and may emit a field. Labels such as
//!    `comercio:` / `terminación` / `en` put it in a context state, and the
//!    tokens that follow are emitted as that field.
//!
//! The movement kind comes from keyword phrases (`compra`, `recibiste`, `spei
//! enviado`…): the earliest phrase in the subject wins, else the earliest in
//! the body; at the same position the longest phrase wins, so `pago
//! recibido` (income) beats `pago` (expense).
//!
//! Bank profiles (`KNOWN_BANKS`) only name the senders and add each bank's own
//! field labels; every bank — known or added by the user — goes through the
//! same reader, which is what lets it work for any bank.

use chrono::{NaiveDate, NaiveDateTime};
use serde::Serialize;

/// Whether the movement takes money out of the account or brings it in.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum NoticeKind {
    Expense,
    Income,
}

impl NoticeKind {
    pub fn as_str(self) -> &'static str {
        match self {
            NoticeKind::Expense => "expense",
            NoticeKind::Income => "income",
        }
    }
}

/// A movement read from a bank notice. `occurred_at` is `YYYY-MM-DD`,
/// `occurred_time` is `HH:MM` (24 h).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BankNotice {
    /// Key of the matched `KNOWN_BANKS` profile; None = read generically.
    pub bank: Option<&'static str>,
    pub kind: NoticeKind,
    pub amount_cents: i64,
    /// ISO code; MXN unless the notice says otherwise.
    pub currency: String,
    pub merchant: Option<String>,
    pub card_last4: Option<String>,
    pub occurred_at: Option<String>,
    pub occurred_time: Option<String>,
}

/// Why a notice did not become a movement.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Rejection {
    /// Marketing: promotions, offers, raffles.
    Promotion,
    /// A statement, a declined/cancelled charge, a login alert… no money moved.
    NotAMovement,
    /// Looks like a movement but carries no amount (e.g. truncated text).
    NoAmount,
    /// Money between the user's own accounts — a payment to their credit
    /// card, money taken out of a savings pocket. Neither income nor expense:
    /// it belongs in the app as a transfer.
    OwnTransfer,
}

/// A bank the reader knows by name: its sender domains and its own field labels.
pub struct BankProfile {
    pub key: &'static str,
    pub name: &'static str,
    /// Sender domains; subdomains match too (`bbva.mx` ⇒ `avisos.bbva.mx`).
    pub domains: &'static [&'static str],
    /// Extra merchant labels this bank uses, normalized (lowercase, no accents).
    pub merchant_labels: &'static [&'static str],
}

pub const KNOWN_BANKS: &[BankProfile] = &[
    BankProfile {
        key: "bbva",
        name: "BBVA",
        domains: &["bbva.mx", "bbva.com", "bbvanet.com.mx"],
        merchant_labels: &["comercio", "establecimiento", "beneficiario", "ordenante"],
    },
    BankProfile {
        key: "nu",
        name: "Nu",
        domains: &["nu.com.mx", "nubank.com.mx", "nu.com"],
        // "Empresa a la cual se realizará el pago: Telcel" on top-ups
        merchant_labels: &[
            "comercio",
            "establecimiento",
            "destinatario",
            "remitente",
            "pago",
        ],
    },
];

/// The address inside a `From` header: `BBVA <avisos@bbva.mx>` → `avisos@bbva.mx`.
pub fn sender_address(from: &str) -> String {
    let s = match (from.rfind('<'), from.rfind('>')) {
        (Some(a), Some(b)) if a < b => &from[a + 1..b],
        _ => from,
    };
    s.trim().trim_matches('"').to_ascii_lowercase()
}

/// Does a sender match a user pattern? A pattern with `@` is a full address;
/// otherwise it is a domain, and subdomains match too.
pub fn sender_matches(from: &str, pattern: &str) -> bool {
    let addr = sender_address(from);
    let pat = pattern.trim().trim_start_matches('@').to_ascii_lowercase();
    if pat.is_empty() {
        return false;
    }
    if pat.contains('@') {
        return addr == pat;
    }
    let Some((_, domain)) = addr.rsplit_once('@') else {
        return false;
    };
    domain == pat || domain.ends_with(&format!(".{pat}"))
}

/// The known bank a sender belongs to, if any.
pub fn known_bank(from: &str) -> Option<&'static BankProfile> {
    KNOWN_BANKS
        .iter()
        .find(|b| b.domains.iter().any(|d| sender_matches(from, d)))
}

/// Read a notice. `received_at` (local wall clock) fills the date/time when
/// the text doesn't state them.
pub fn read_notice(
    from: &str,
    subject: &str,
    body: &str,
    received_at: Option<NaiveDateTime>,
) -> Result<BankNotice, Rejection> {
    let profile = known_bank(from);
    let subj = tokenize(subject);
    let body_toks = tokenize(body);

    if has_phrase(&subj, PROMO_PHRASES) {
        return Err(Rejection::Promotion);
    }
    if has_phrase(&subj, OWN_TRANSFER_SUBJECT) || has_phrase(&body_toks, OWN_TRANSFER_PHRASES) {
        return Err(Rejection::OwnTransfer);
    }
    if has_phrase(&subj, NOT_MOVEMENT_PHRASES)
        || has_phrase(&subj, DECLINED_PHRASES)
        || has_phrase(&subj, CANCELLED_PHRASES)
        || has_phrase(&body_toks, DECLINED_PHRASES)
    {
        return Err(Rejection::NotAMovement);
    }

    let subj_kind = find_kind(&subj);
    let body_kind = find_kind(&body_toks);
    let Some(kind) = subj_kind.or(body_kind).map(|(k, _)| k) else {
        return Err(Rejection::NotAMovement);
    };

    // Fields: the body first; the subject fills whatever the body lacks (Nu
    // puts amount and merchant in the subject of some notices).
    let labels = profile.map(|p| p.merchant_labels).unwrap_or(&[]);
    let from_body = Transducer::run(&body_toks, body_kind.map(|(_, at)| at), labels);
    let from_subj = Transducer::run(&subj, subj_kind.map(|(_, at)| at), labels);

    let Some((amount_cents, currency)) = from_body.amount.or(from_subj.amount) else {
        return Err(Rejection::NoAmount);
    };
    if amount_cents <= 0 {
        return Err(Rejection::NoAmount);
    }

    let occurred_at = from_body
        .date
        .or(from_subj.date)
        .or_else(|| received_at.map(|d| d.date()))
        .map(|d| d.format("%Y-%m-%d").to_string());
    let occurred_time = from_body.time.or(from_subj.time).or_else(|| {
        // only borrow the clock when the date came from it too
        let text_has_date = from_body.date.is_some() || from_subj.date.is_some();
        (!text_has_date)
            .then(|| received_at.map(|d| d.format("%H:%M").to_string()))
            .flatten()
    });

    Ok(BankNotice {
        bank: profile.map(|p| p.key),
        kind,
        amount_cents,
        currency,
        merchant: from_body
            .merchant
            .or_else(|| counterpart(&body_toks))
            .or(from_subj.merchant),
        card_last4: from_body.card.or(from_subj.card),
        occurred_at,
        occurred_time,
    })
}

/// Days apart a notice and an existing movement can be and still look like
/// the same one (a purchase entered by hand the day after, a notice that
/// arrives late at night).
pub const DUPLICATE_WINDOW_DAYS: i64 = 2;

/// Does an existing movement look like this notice entered by hand? Same
/// direction, same amount, dates within `DUPLICATE_WINDOW_DAYS`.
/// `existing_kind` is the transactions.kind ('expense', 'transfer_out'…).
pub fn looks_like_same_movement(
    notice_kind: NoticeKind,
    notice_cents: i64,
    notice_date: &str,
    existing_kind: &str,
    existing_cents: i64,
    existing_date: &str,
) -> bool {
    let same_direction = match notice_kind {
        NoticeKind::Expense => matches!(existing_kind, "expense" | "transfer_out"),
        NoticeKind::Income => matches!(existing_kind, "income" | "transfer_in"),
    };
    let (Ok(a), Ok(b)) = (
        NaiveDate::parse_from_str(notice_date, "%Y-%m-%d"),
        NaiveDate::parse_from_str(existing_date, "%Y-%m-%d"),
    ) else {
        return false;
    };
    same_direction
        && notice_cents == existing_cents
        && (a - b).num_days().abs() <= DUPLICATE_WINDOW_DAYS
}

// ---------------------------------------------------------------- keywords --

/// Movement phrases, normalized. Earliest match wins; longest at a tie.
const KIND_PHRASES: &[(&[&str], NoticeKind)] = &[
    // income
    (&["recibiste"], NoticeKind::Income),
    (&["te", "enviaron"], NoticeKind::Income),
    (&["te", "depositaron"], NoticeKind::Income),
    (&["te", "transfirieron"], NoticeKind::Income),
    (&["deposito"], NoticeKind::Income),
    (&["abono"], NoticeKind::Income),
    (&["devolucion"], NoticeKind::Income),
    (&["reembolso"], NoticeKind::Income),
    (&["pago", "recibido"], NoticeKind::Income),
    (&["transferencia", "recibida"], NoticeKind::Income),
    (&["spei", "recibido"], NoticeKind::Income),
    (&["transferencia", "spei", "recibida"], NoticeKind::Income),
    (&["has", "recibido"], NoticeKind::Income),
    (&["recepcion", "de"], NoticeKind::Income),
    (&["ingreso"], NoticeKind::Income),
    // expense
    (&["compra"], NoticeKind::Expense),
    (&["compraste"], NoticeKind::Expense),
    (&["cargo"], NoticeKind::Expense),
    (&["pago"], NoticeKind::Expense),
    (&["pagaste"], NoticeKind::Expense),
    (&["retiro"], NoticeKind::Expense),
    (&["retiraste"], NoticeKind::Expense),
    (&["enviaste"], NoticeKind::Expense),
    (&["transferiste"], NoticeKind::Expense),
    (&["transferencia", "enviada"], NoticeKind::Expense),
    (&["spei", "enviado"], NoticeKind::Expense),
    (&["transferencia", "spei", "enviada"], NoticeKind::Expense),
    // BBVA: "La transferencia a cuenta de débito fue exitosa"
    (&["transferencia", "a", "cuenta"], NoticeKind::Expense),
    (&["transferencia", "exitosa"], NoticeKind::Expense),
    (&["transferencia", "enviada"], NoticeKind::Expense),
    (&["has", "enviado"], NoticeKind::Expense),
    (&["envio", "de"], NoticeKind::Expense),
    (&["domiciliacion"], NoticeKind::Expense),
    (&["cobro"], NoticeKind::Expense),
    (&["consumo"], NoticeKind::Expense),
];

/// In a subject, marketing. Matched as whole words.
const PROMO_PHRASES: &[&[&str]] = &[
    &["promocion"],
    &["promociones"],
    &["oferta"],
    &["ofertas"],
    &["aprovecha"],
    &["descuento"],
    &["descuentos"],
    &["sorteo"],
    &["participa"],
    &["gana"],
    &["preaprobado"],
    &["preautorizado"],
    &["cashback", "extra"],
    &["webinar"],
    &["regalo"],
    &["llevate"],
    &["beneficios"],
    &["novedades"],
    &["noticias"],
    &["sabias"],
    &["descubre"],
];

/// In a subject: money between the user's own accounts (seen in real BBVA and
/// Nu notices: "¡Recibimos tu pago!", "Retiraste dinero de tus apartados").
const OWN_TRANSFER_SUBJECT: &[&[&str]] = &[
    &["recibimos", "tu", "pago"],
    &["apartado"],
    &["apartados"],
    &["pago", "a", "tu", "tarjeta"],
];

/// In the body: Nu's own wording only. A looser "pago a tu tarjeta" also turns
/// up in footers of real charges ("recuerda domiciliar el pago a tu tarjeta de
/// crédito") and would drop them; the real notices are caught by the subject.
const OWN_TRANSFER_PHRASES: &[&[&str]] = &[&["pago", "que", "hiciste", "a", "tu", "tarjeta"]];

/// "<who> hizo una transferencia…", "<who> te envió…": the counterpart is the
/// run of words right before the phrase (Nu names the payer this way).
const COUNTERPART_PHRASES: &[&[&str]] = &[
    &["hizo", "una", "transferencia"],
    &["te", "envio"],
    &["te", "transfirio"],
    &["te", "deposito"],
];

/// The counterpart named before a `COUNTERPART_PHRASES` phrase, if any.
fn counterpart(toks: &[Tok]) -> Option<String> {
    for (i, _) in toks.iter().enumerate() {
        let hit = COUNTERPART_PHRASES.iter().any(|p| {
            p.iter()
                .enumerate()
                .all(|(k, w)| matches!(toks.get(i + k), Some(Tok::Word { norm, .. }) if norm == w))
        });
        if !hit {
            continue;
        }
        let mut words: Vec<&str> = toks[..i]
            .iter()
            .rev()
            .map_while(|t| match t {
                Tok::Word { raw, .. } => Some(raw.as_str()),
                _ => None,
            })
            .take(8)
            .collect();
        words.reverse();
        if !words.is_empty() {
            return Some(words.join(" "));
        }
    }
    None
}

/// In a subject, not a movement.
const NOT_MOVEMENT_PHRASES: &[&[&str]] = &[
    &["estado", "de", "cuenta"],
    &["inicio", "de", "sesion"],
    &["codigo", "de", "verificacion"],
    &["fecha", "limite", "de", "pago"],
    &["recordatorio"],
    &["actualiza"],
    &["terminos", "y", "condiciones"],
];

/// Anywhere: the charge did not go through.
const DECLINED_PHRASES: &[&[&str]] = &[
    &["rechazada"],
    &["rechazado"],
    &["declinada"],
    &["declinado"],
    &["no", "fue", "aprobada"],
    &["no", "aprobada"],
    &["no", "se", "pudo"],
];

/// In a subject only: banks' footers say things like "si tu tarjeta fue
/// cancelada, llámanos", which must not drop a real charge.
const CANCELLED_PHRASES: &[&[&str]] = &[&["cancelada"], &["cancelado"]];

/// Generic merchant/counterpart labels (followed by `:`).
const MERCHANT_LABELS: &[&str] = &[
    "comercio",
    "establecimiento",
    "negocio",
    "concepto",
    "beneficiario",
    "destinatario",
    "ordenante",
    "remitente",
];

/// Words before an amount that make it a balance, not the movement. "crédito"
/// only counts on its own ("crédito disponible"): right after "tarjeta (de)" it
/// names the card, and the card context swallows it first.
const BALANCE_WORDS: &[&str] = &["saldo", "disponible", "limite", "credito"];

/// Words that end a merchant run after `en` / `a` / `de`.
const MERCHANT_STOP: &[&str] = &[
    "con",
    "por",
    "el",
    "la",
    "los",
    "las",
    "a",
    "al",
    "desde",
    "tu",
    "tarjeta",
    "cuenta",
    "terminacion",
    "termina",
    "fue",
    "ha",
    "se",
    "y",
    "monto",
    "importe",
    "hoy",
    "que",
    "para",
    "en",
    "el",
];

fn find_kind(toks: &[Tok]) -> Option<(NoticeKind, usize)> {
    let words: Vec<(usize, &str)> = toks
        .iter()
        .enumerate()
        .filter_map(|(i, t)| match t {
            Tok::Word { norm, .. } => Some((i, norm.as_str())),
            _ => None,
        })
        .collect();
    for w in 0..words.len() {
        let mut best: Option<(NoticeKind, usize)> = None;
        for (phrase, kind) in KIND_PHRASES {
            if words_match(&words[w..], phrase) && best.is_none_or(|(_, len)| phrase.len() > len) {
                best = Some((*kind, phrase.len()));
            }
        }
        if let Some((kind, len)) = best {
            // position = token index just past the phrase
            return Some((kind, words[w + len - 1].0 + 1));
        }
    }
    None
}

fn words_match(words: &[(usize, &str)], phrase: &[&str]) -> bool {
    phrase.len() <= words.len() && phrase.iter().zip(words).all(|(p, (_, w))| p == w)
}

fn has_phrase(toks: &[Tok], phrases: &[&[&str]]) -> bool {
    let words: Vec<(usize, &str)> = toks
        .iter()
        .enumerate()
        .filter_map(|(i, t)| match t {
            Tok::Word { norm, .. } => Some((i, norm.as_str())),
            _ => None,
        })
        .collect();
    (0..words.len()).any(|w| phrases.iter().any(|p| words_match(&words[w..], p)))
}

// -------------------------------------------------------------- transducer --

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum State {
    /// Default: emits amounts, masked cards, dates and times as they come.
    Scan,
    /// Saw a card word (`terminación`, `tarjeta`…): the next 4-digit run is the
    /// card ending. Counts down the tokens it will wait.
    Card(u8),
    /// Saw a merchant label or `en` after the movement phrase: collecting.
    Merchant { labelled: bool },
    /// Saw a balance word: the next amount is a balance, skip it.
    Balance(u8),
}

#[derive(Default)]
struct Fields {
    amount: Option<(i64, String)>,
    /// First amount found before the movement phrase; used only if none follows.
    early_amount: Option<(i64, String)>,
    card: Option<String>,
    merchant: Option<String>,
    /// The merchant came from a label ("Destinatario:"), not a guess after
    /// "en"/"a"/"de"; a label overrides a guess, never the other way round.
    merchant_labelled: bool,
    date: Option<NaiveDate>,
    time: Option<String>,
}

struct Transducer<'a> {
    state: State,
    out: Fields,
    /// Merchant being collected, original spelling.
    merchant_buf: String,
    /// The last thing appended was glue punctuation (`.`, `-`…): no space next.
    glued: bool,
    /// Tokens since the movement phrase or the last amount. `en`/`a`/`de` open
    /// a merchant only right after one of those anchors ("compra en OXXO",
    /// "$350.00 de María"), never in "fecha de operación".
    since_anchor: usize,
    labels: &'a [&'a str],
    /// The word before the current one: "Método de pago:" is how it was
    /// paid, not who was paid.
    prev_word: Option<&'a str>,
}

impl<'a> Transducer<'a> {
    /// `kind_at`: token index just past the movement phrase, if this text has one.
    fn run(toks: &'a [Tok], kind_at: Option<usize>, labels: &'a [&'a str]) -> Fields {
        let mut t = Transducer {
            state: State::Scan,
            out: Fields::default(),
            merchant_buf: String::new(),
            glued: false,
            since_anchor: usize::MAX,
            prev_word: None,
            labels,
        };
        for (i, tok) in toks.iter().enumerate() {
            if kind_at == Some(i) {
                t.since_anchor = 0;
            }
            let after_kind = kind_at.is_some_and(|k| i >= k);
            let next = toks.get(i + 1);
            t.step(tok, next, after_kind);
            match tok {
                Tok::Word { norm, .. } => t.prev_word = Some(norm.as_str()),
                Tok::Punct(':') => {}
                _ => t.prev_word = None,
            }
            t.since_anchor = if matches!(tok, Tok::Money(..)) {
                0
            } else {
                t.since_anchor.saturating_add(1)
            };
        }
        t.flush_merchant();
        let mut out = t.out;
        if out.amount.is_none() {
            out.amount = out.early_amount.take();
        }
        out
    }

    fn is_label(&self, w: &str) -> bool {
        MERCHANT_LABELS.contains(&w) || self.labels.contains(&w)
    }

    fn push_word(&mut self, w: &str) {
        if !self.merchant_buf.is_empty() && !self.glued {
            self.merchant_buf.push(' ');
        }
        self.merchant_buf.push_str(w);
        self.glued = false;
    }

    fn push_glue(&mut self, c: char) {
        if c == '&' {
            self.merchant_buf.push_str(" &");
            self.glued = false;
        } else {
            self.merchant_buf.push(c);
            self.glued = true;
        }
    }

    fn flush_merchant(&mut self) {
        let labelled = matches!(self.state, State::Merchant { labelled: true });
        let replace = self.out.merchant.is_none() || (labelled && !self.out.merchant_labelled);
        if replace && !self.merchant_buf.is_empty() {
            let name = self
                .merchant_buf
                .trim_matches(|c: char| !c.is_alphanumeric())
                .to_string();
            if name.chars().any(|c| c.is_alphabetic()) {
                self.out.merchant = Some(name);
                self.out.merchant_labelled = labelled;
            }
        }
        self.merchant_buf.clear();
        self.glued = false;
    }

    fn emit_amount(&mut self, cents: i64, cur: &str, after_kind: bool) {
        let slot = if after_kind {
            &mut self.out.amount
        } else {
            &mut self.out.early_amount
        };
        if slot.is_none() {
            *slot = Some((cents, cur.to_string()));
        }
    }

    fn step(&mut self, tok: &'a Tok, next: Option<&Tok>, after_kind: bool) {
        // Tokens every state emits regardless of context.
        match tok {
            Tok::Date(d) => {
                self.out.date.get_or_insert(*d);
            }
            Tok::Time(t) if self.out.time.is_none() => self.out.time = Some(t.clone()),
            Tok::Masked(last4) if self.out.card.is_none() => self.out.card = Some(last4.clone()),
            _ => {}
        }

        self.state = match (self.state, tok) {
            // ---- merchant collection ----
            (State::Merchant { labelled }, Tok::Word { raw, norm }) => {
                let stop = !labelled && MERCHANT_STOP.contains(&norm.as_str());
                // "Destinatario: JUAN PÉREZ Monto: $700": the next label ends it
                let next_label =
                    matches!(next, Some(Tok::Punct(':'))) && !self.merchant_buf.is_empty();
                if stop || next_label || self.merchant_buf.len() >= 60 {
                    self.flush_merchant();
                    self.scan_word(norm, next, after_kind)
                } else {
                    self.push_word(raw);
                    State::Merchant { labelled }
                }
            }
            (State::Merchant { .. }, Tok::Digits(d)) if !self.merchant_buf.is_empty() => {
                // store numbers like "OXXO 1234" are part of the name
                self.push_word(d);
                self.state
            }
            (State::Merchant { labelled }, Tok::Punct(c))
                if !self.merchant_buf.is_empty()
                    && matches!(c, '*' | '&' | '-' | '.' | '\'')
                    && matches!(next, Some(Tok::Word { .. } | Tok::Digits(_))) =>
            {
                self.push_glue(*c);
                State::Merchant { labelled }
            }
            (State::Merchant { .. }, Tok::Punct(':')) if self.merchant_buf.is_empty() => self.state,
            (State::Merchant { .. }, Tok::Money(c, cur)) => {
                self.flush_merchant();
                self.emit_amount(*c, cur, after_kind);
                State::Scan
            }
            (State::Merchant { .. }, _) => {
                self.flush_merchant();
                State::Scan
            }

            // ---- card ending ----
            (State::Card(_), Tok::Digits(d)) if d.len() == 4 => {
                if self.out.card.is_none() {
                    self.out.card = Some(d.clone());
                }
                State::Scan
            }
            (State::Card(_), Tok::Masked(_)) => State::Scan,
            (State::Card(n), Tok::Word { norm, .. }) if n > 0 => {
                if CARD_WORDS.contains(&norm.as_str()) {
                    // "tarjeta de crédito terminación …": a new card word
                    // restarts the wait for the digits
                    State::Card(4)
                } else if CARD_CONT.contains(&norm.as_str()) || norm.len() <= 3 {
                    State::Card(n - 1)
                } else {
                    self.scan_word(norm, next, after_kind)
                }
            }
            (State::Card(n), Tok::Punct(_)) if n > 0 => State::Card(n - 1),

            // ---- balance amounts are skipped ----
            (State::Balance(_), Tok::Money(..)) => State::Scan,
            (State::Balance(n), Tok::Word { norm, .. }) if n > 0 => {
                if BALANCE_WORDS.contains(&norm.as_str()) {
                    State::Balance(3)
                } else {
                    State::Balance(n - 1)
                }
            }
            (State::Balance(n), Tok::Punct(_)) if n > 0 => State::Balance(n - 1),

            // ---- scan ----
            (_, Tok::Money(c, cur)) => {
                self.emit_amount(*c, cur, after_kind);
                State::Scan
            }
            (_, Tok::Word { norm, .. }) => self.scan_word(norm, next, after_kind),
            _ => State::Scan,
        };
    }

    /// Transition out of Scan on a word.
    fn scan_word(&mut self, norm: &str, next: Option<&Tok>, after_kind: bool) -> State {
        let next_is_colon = matches!(next, Some(Tok::Punct(':')));
        if BALANCE_WORDS.contains(&norm) {
            return State::Balance(3);
        }
        if CARD_WORDS.contains(&norm) {
            return State::Card(4);
        }
        let means_of_payment = matches!(self.prev_word, Some("de")) && matches!(norm, "pago")
            || matches!(self.prev_word, Some("metodo" | "forma" | "medio"));
        if !self.out.merchant_labelled && self.is_label(norm) && next_is_colon && !means_of_payment
        {
            return State::Merchant { labelled: true };
        }
        if self.out.merchant.is_none()
            && after_kind
            && self.since_anchor <= 3
            && matches!(norm, "en" | "a" | "de")
        {
            // "compra en OXXO", "enviaste a Juan", "recibiste de María"
            if matches!(next, Some(Tok::Word { .. })) {
                return State::Merchant { labelled: false };
            }
        }
        State::Scan
    }
}

/// Words that announce a card/account ending.
const CARD_WORDS: &[&str] = &["terminacion", "termina", "terminada", "tarjeta", "cuenta"];

/// Words a card context may skip on its way to the digits.
/// "crédito"/"débito" here describe the card ("tarjeta de crédito"), so they
/// never reach the balance check.
const CARD_CONT: &[&str] = &[
    "no", "numero", "en", "con", "de", "credito", "debito", "nomina", "digital", "virtual",
];

// --------------------------------------------------------------- tokenizer --

#[derive(Debug, Clone, PartialEq, Eq)]
enum Tok {
    Word {
        raw: String,
        norm: String,
    },
    /// Amount in cents and its ISO currency.
    Money(i64, String),
    Digits(String),
    /// Masked card/account number: `****1234`, `*1234`, `XXXX1234` → last 4.
    Masked(String),
    Date(NaiveDate),
    /// `HH:MM`, 24 h.
    Time(String),
    Punct(char),
    /// Line break: ends merchant runs.
    Newline,
}

/// Lowercase and fold Spanish accents; other characters pass through.
fn fold(c: char) -> char {
    match c {
        'á' | 'à' | 'ä' | 'Á' | 'À' | 'Ä' => 'a',
        'é' | 'è' | 'ë' | 'É' | 'È' | 'Ë' => 'e',
        'í' | 'ì' | 'ï' | 'Í' | 'Ì' | 'Ï' => 'i',
        'ó' | 'ò' | 'ö' | 'Ó' | 'Ò' | 'Ö' => 'o',
        'ú' | 'ù' | 'ü' | 'Ú' | 'Ù' | 'Ü' => 'u',
        'ñ' | 'Ñ' => 'n',
        _ => c.to_ascii_lowercase(),
    }
}

const MONTHS: &[(&str, u32)] = &[
    ("enero", 1),
    ("ene", 1),
    ("febrero", 2),
    ("feb", 2),
    ("marzo", 3),
    ("mar", 3),
    ("abril", 4),
    ("abr", 4),
    ("mayo", 5),
    ("may", 5),
    ("junio", 6),
    ("jun", 6),
    ("julio", 7),
    ("jul", 7),
    ("agosto", 8),
    ("ago", 8),
    ("septiembre", 9),
    ("setiembre", 9),
    ("sep", 9),
    ("sept", 9),
    ("octubre", 10),
    ("oct", 10),
    ("noviembre", 11),
    ("nov", 11),
    ("diciembre", 12),
    ("dic", 12),
];

fn month(word: &str) -> Option<u32> {
    MONTHS.iter().find(|(m, _)| *m == word).map(|(_, n)| *n)
}

fn year(n: &str) -> Option<i32> {
    let y: i32 = n.parse().ok()?;
    match n.len() {
        4 => Some(y),
        2 => Some(2000 + y),
        _ => None,
    }
}

/// Raw lexemes before money/date/time recognition.
#[derive(Debug, Clone, PartialEq)]
enum Lex {
    Word(String, String),
    /// Digits with `,`/`.` kept, e.g. `1,234.56`.
    Num(String),
    Punct(char),
    Newline,
}

fn lex(text: &str) -> Vec<Lex> {
    let chars: Vec<char> = text.chars().collect();
    let mut out = Vec::new();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if c == '\n' {
            if out.last() != Some(&Lex::Newline) {
                out.push(Lex::Newline);
            }
            i += 1;
        } else if c.is_whitespace() {
            i += 1;
        } else if c.is_ascii_digit() {
            let start = i;
            while i < chars.len()
                && (chars[i].is_ascii_digit()
                    || ((chars[i] == ',' || chars[i] == '.')
                        && chars.get(i + 1).is_some_and(|n| n.is_ascii_digit())))
            {
                i += 1;
            }
            out.push(Lex::Num(chars[start..i].iter().collect()));
        } else if c.is_alphabetic() {
            let start = i;
            while i < chars.len() && (chars[i].is_alphanumeric()) {
                i += 1;
            }
            let raw: String = chars[start..i].iter().collect();
            let norm: String = raw.chars().map(fold).collect();
            out.push(Lex::Word(raw, norm));
        } else if c == '.'
            && chars.get(i + 1).is_none_or(|n| n.is_whitespace())
            && !matches!(out.last(), Some(Lex::Word(raw, _)) if raw.chars().count() <= 2)
        {
            // a full stop ends a sentence like a line break ends a cell:
            // "en AMAZON. Recuerda…" must not glue the next sentence on. Not
            // after a one/two-letter abbreviation: "7:15 p. m.", "No. 1234".
            if out.last() != Some(&Lex::Newline) {
                out.push(Lex::Newline);
            }
            i += 1;
        } else {
            out.push(Lex::Punct(c));
            i += 1;
        }
    }
    out
}

/// `1,234.56` → 123456 cents. One or two digits after the last separator are
/// decimals (`12.5` = 1250, a decimal comma `1.234,56` too); three are a
/// thousands group (`12,000`).
fn parse_cents(num: &str) -> Option<i64> {
    let (int_part, frac) = match num.rfind(['.', ',']) {
        Some(p) if matches!(num.len() - p - 1, 1 | 2) => (&num[..p], &num[p + 1..]),
        _ => (num, ""),
    };
    let int_digits: String = int_part.chars().filter(|c| c.is_ascii_digit()).collect();
    if int_digits.is_empty() || int_digits.len() > 13 {
        return None;
    }
    let units: i64 = int_digits.parse().ok()?;
    let cents: i64 = match frac.len() {
        0 => 0,
        1 => frac.parse::<i64>().ok()? * 10,
        _ => frac.parse().ok()?,
    };
    Some(units * 100 + cents)
}

fn currency_word(norm: &str) -> Option<&'static str> {
    match norm {
        "mxn" | "mn" | "pesos" | "mxp" => Some("MXN"),
        "usd" | "dolares" | "dls" | "dlls" => Some("USD"),
        "eur" | "euros" => Some("EUR"),
        _ => None,
    }
}

fn tokenize(text: &str) -> Vec<Tok> {
    let lx = lex(text);
    let mut out = Vec::with_capacity(lx.len());
    let mut i = 0;
    while i < lx.len() {
        let rest = &lx[i..];
        // `$ 1,234.56 [MXN]`, `US$ 12.00`
        if let [Lex::Punct('$'), Lex::Num(n), ..] = rest {
            if let Some(c) = parse_cents(n) {
                let mut cur = "MXN";
                let mut used = 2;
                if let Some(Lex::Word(_, w)) = rest.get(2) {
                    if let Some(cc) = currency_word(w) {
                        cur = cc;
                        used = 3;
                    }
                }
                if let Some(Tok::Word { norm, .. }) = out.last() {
                    if norm == "us" || norm == "usd" {
                        out.pop();
                        cur = "USD";
                    }
                }
                out.push(Tok::Money(c, cur.to_string()));
                i += used;
                continue;
            }
        }
        // `MXN 1,234.56` / `USD 12.00`
        if let [Lex::Word(_, w), Lex::Num(n), ..] = rest {
            if let (Some(cur), Some(c)) = (currency_word(w), parse_cents(n)) {
                out.push(Tok::Money(c, cur.to_string()));
                i += 2;
                continue;
            }
        }
        // masked: `****1234`, `*1234`, `XXXX1234`, `•••• 1234`
        if let [Lex::Punct(p), ..] = rest {
            if matches!(p, '*' | '•' | '·') {
                let mut j = 0;
                while matches!(rest.get(j), Some(Lex::Punct('*' | '•' | '·'))) {
                    j += 1;
                }
                if let Some(Lex::Num(n)) = rest.get(j) {
                    if n.len() >= 4 && n.chars().all(|c| c.is_ascii_digit()) {
                        out.push(Tok::Masked(n[n.len() - 4..].to_string()));
                        i += j + 1;
                        continue;
                    }
                }
            }
        }
        if let [Lex::Word(raw, _), ..] = rest {
            let up = raw.to_ascii_lowercase();
            if up.len() >= 6 && up.starts_with("xx") {
                let digits: String = up.chars().filter(|c| c.is_ascii_digit()).collect();
                if digits.len() == 4 && up.trim_start_matches('x') == digits {
                    out.push(Tok::Masked(digits));
                    i += 1;
                    continue;
                }
            }
        }
        // dates: dd/mm/yyyy, dd-mm-yy, yyyy-mm-dd, dd/mmm/yyyy, "6 de noviembre de 2026"
        if let Some((d, used)) = date_at(rest) {
            out.push(Tok::Date(d));
            i += used;
            continue;
        }
        // time: hh:mm[:ss] [am|pm|hrs]
        if let Some((t, used)) = time_at(rest) {
            out.push(Tok::Time(t));
            i += used;
            continue;
        }
        // amount next to a money label: "Importe: 250.00", "monto 1,200.00"
        if let [Lex::Num(n), ..] = rest {
            let labelled = out.iter().rev().take(2).any(|t| {
                matches!(t, Tok::Word { norm, .. }
                    if matches!(norm.as_str(), "importe" | "monto" | "cantidad" | "total" | "por"))
            });
            if labelled && (n.contains('.') || n.contains(',')) {
                if let Some(c) = parse_cents(n) {
                    let mut used = 1;
                    let mut cur = "MXN";
                    if let Some(Lex::Word(_, w)) = rest.get(1) {
                        if let Some(cc) = currency_word(w) {
                            cur = cc;
                            used = 2;
                        }
                    }
                    out.push(Tok::Money(c, cur.to_string()));
                    i += used;
                    continue;
                }
            }
        }
        out.push(match &rest[0] {
            Lex::Word(raw, norm) => Tok::Word {
                raw: raw.clone(),
                norm: norm.clone(),
            },
            Lex::Num(n) => Tok::Digits(n.clone()),
            Lex::Punct(c) => Tok::Punct(*c),
            Lex::Newline => Tok::Newline,
        });
        i += 1;
    }
    out
}

fn date_at(rest: &[Lex]) -> Option<(NaiveDate, usize)> {
    match rest {
        // dd/mm/yyyy, dd-mm-yyyy, yyyy-mm-dd
        [Lex::Num(a), Lex::Punct(s1), Lex::Num(b), Lex::Punct(s2), Lex::Num(c), ..]
            if s1 == s2 && matches!(s1, '/' | '-') =>
        {
            let (y, m, d) = if a.len() == 4 {
                (year(a)?, b.parse().ok()?, c.parse().ok()?)
            } else {
                (year(c)?, b.parse().ok()?, a.parse().ok()?)
            };
            NaiveDate::from_ymd_opt(y, m, d).map(|dt| (dt, 5))
        }
        // dd/nov/yyyy
        [Lex::Num(a), Lex::Punct(s1), Lex::Word(_, m), Lex::Punct(s2), Lex::Num(c), ..]
            if s1 == s2 && matches!(s1, '/' | '-') =>
        {
            NaiveDate::from_ymd_opt(year(c)?, month(m)?, a.parse().ok()?).map(|dt| (dt, 5))
        }
        // 6 de noviembre de 2026 / 6 noviembre 2026
        [Lex::Num(a), Lex::Word(_, de1), Lex::Word(_, m), Lex::Word(_, de2), Lex::Num(c), ..]
            if de1 == "de" && de2 == "de" =>
        {
            NaiveDate::from_ymd_opt(year(c)?, month(m)?, a.parse().ok()?).map(|dt| (dt, 5))
        }
        [Lex::Num(a), Lex::Word(_, m), Lex::Num(c), ..] if a.len() <= 2 && c.len() == 4 => {
            NaiveDate::from_ymd_opt(year(c)?, month(m)?, a.parse().ok()?).map(|dt| (dt, 3))
        }
        _ => None,
    }
}

fn time_at(rest: &[Lex]) -> Option<(String, usize)> {
    let [Lex::Num(h), Lex::Punct(':'), Lex::Num(m), ..] = rest else {
        return None;
    };
    if h.len() > 2 || m.len() != 2 {
        return None;
    }
    let mut hh: u32 = h.parse().ok()?;
    let mm: u32 = m.parse().ok()?;
    let mut used = 3;
    // optional :ss
    if let [Lex::Punct(':'), Lex::Num(s), ..] = &rest[3..] {
        if s.len() == 2 {
            used += 2;
        }
    }
    // optional am/pm ("p.m." lexes as p . m .)
    match &rest[used..] {
        [Lex::Word(_, w), ..] if w == "pm" || w == "am" => {
            if w == "pm" && hh < 12 {
                hh += 12;
            } else if w == "am" && hh == 12 {
                hh = 0;
            }
            used += 1;
        }
        [Lex::Word(_, p), Lex::Punct('.'), Lex::Word(_, m2), ..]
            if (p == "p" || p == "a") && m2 == "m" =>
        {
            if p == "p" && hh < 12 {
                hh += 12;
            } else if p == "a" && hh == 12 {
                hh = 0;
            }
            used += 3;
            if matches!(rest.get(used), Some(Lex::Punct('.'))) {
                used += 1;
            }
        }
        _ => {}
    }
    if hh > 23 || mm > 59 {
        return None;
    }
    Some((format!("{hh:02}:{mm:02}"), used))
}

// ------------------------------------------------------------- html → text --

/// Plain text out of an HTML email: drops `<head>`, `<style>`, `<script>`,
/// turns block ends and `<br>` into line breaks, decodes common entities.
pub fn html_to_text(html: &str) -> String {
    let mut out = String::with_capacity(html.len() / 3);
    let lower = html.to_ascii_lowercase();
    let bytes = html.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'<' {
            let Some(end) = lower[i..].find('>').map(|e| i + e) else {
                break;
            };
            let tag = lower[i + 1..end].trim_start_matches('/');
            let name: String = tag
                .chars()
                .take_while(|c| c.is_ascii_alphanumeric())
                .collect();
            if !lower[i + 1..end].starts_with('/')
                && matches!(name.as_str(), "style" | "script" | "head" | "title")
            {
                let close = format!("</{name}");
                i = lower[end..]
                    .find(&close)
                    .and_then(|c| lower[end + c..].find('>').map(|g| end + c + g + 1))
                    .unwrap_or(bytes.len());
                continue;
            }
            match name.as_str() {
                "br" | "p" | "div" | "tr" | "li" | "h1" | "h2" | "h3" | "h4" | "table" | "hr" => {
                    out.push('\n')
                }
                "td" | "th" => out.push(' '),
                _ => {}
            }
            i = end + 1;
        } else if bytes[i] == b'&' {
            let semi = lower[i..].find(';').filter(|s| *s <= 10);
            if let Some(s) = semi {
                let ent = &lower[i + 1..i + s];
                if let Some(c) = entity(ent) {
                    out.push(c);
                    i += s + 1;
                    continue;
                }
            }
            out.push('&');
            i += 1;
        } else {
            let ch = html[i..].chars().next().unwrap_or(' ');
            out.push(ch);
            i += ch.len_utf8();
        }
    }
    // collapse spaces per line, drop empty lines
    out.lines()
        .map(|l| l.split_whitespace().collect::<Vec<_>>().join(" "))
        .filter(|l| !l.is_empty())
        .collect::<Vec<_>>()
        .join("\n")
}

fn entity(e: &str) -> Option<char> {
    if let Some(num) = e.strip_prefix('#') {
        let code = match num.strip_prefix('x') {
            Some(hex) => u32::from_str_radix(hex, 16).ok()?,
            None => num.parse().ok()?,
        };
        return char::from_u32(code);
    }
    Some(match e {
        "nbsp" => ' ',
        "amp" => '&',
        "lt" => '<',
        "gt" => '>',
        "quot" => '"',
        "apos" => '\'',
        "aacute" => 'á',
        "eacute" => 'é',
        "iacute" => 'í',
        "oacute" => 'ó',
        "uacute" => 'ú',
        "ntilde" => 'ñ',
        "uuml" => 'ü',
        "iexcl" => '¡',
        "iquest" => '¿',
        "bull" => '•',
        "middot" => '·',
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(s: &str) -> NaiveDateTime {
        NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M").unwrap()
    }

    // Samples are real notice layouts with names, numbers and references
    // replaced. Every amount is checked by hand: "$1,234.56" = 1234 pesos and
    // 56 centavos = 1234×100 + 56 = 123456 cents.

    #[test]
    fn bbva_card_purchase() {
        let body = "Estimado cliente:\n\
            Te informamos que se realizó una compra con tu tarjeta de crédito BBVA.\n\
            Comercio: OXXO SUC 1234\n\
            Importe: $1,234.56 MXN\n\
            Tarjeta: ****9876\n\
            Fecha: 05/11/2026\nHora: 14:35:10\n\
            Saldo disponible: $8,765.44";
        let n = read_notice(
            "BBVA <notificaciones@bbva.mx>",
            "Notificación de compra",
            body,
            None,
        )
        .unwrap();
        assert_eq!(n.bank, Some("bbva"));
        assert_eq!(n.kind, NoticeKind::Expense);
        assert_eq!(n.amount_cents, 123_456);
        assert_eq!(n.currency, "MXN");
        assert_eq!(n.merchant.as_deref(), Some("OXXO SUC 1234"));
        assert_eq!(n.card_last4.as_deref(), Some("9876"));
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-05"));
        assert_eq!(n.occurred_time.as_deref(), Some("14:35"));
    }

    #[test]
    fn bbva_spei_sent() {
        let body = "Transferencia SPEI enviada\n\
            Has enviado una transferencia desde tu cuenta terminación 4321.\n\
            Beneficiario: Juan Pérez López\n\
            Monto: $2,500.00\n\
            Concepto: renta\n\
            Fecha de operación: 6 de noviembre de 2026, 09:05 h";
        let n = read_notice("avisos@bbva.mx", "SPEI enviado", body, None).unwrap();
        assert_eq!(n.kind, NoticeKind::Expense);
        // $2,500.00 = 2500×100 = 250000
        assert_eq!(n.amount_cents, 250_000);
        assert_eq!(n.merchant.as_deref(), Some("Juan Pérez López"));
        assert_eq!(n.card_last4.as_deref(), Some("4321"));
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-06"));
        assert_eq!(n.occurred_time.as_deref(), Some("09:05"));
    }

    #[test]
    fn bbva_spei_received() {
        let body = "<html><head><style>p{color:red}</style></head><body>\
            <p>Recibiste una transferencia SPEI en tu cuenta terminaci&oacute;n 4321</p>\
            <table><tr><td>Ordenante:</td><td>EMPRESA EJEMPLO SA DE CV</td></tr>\
            <tr><td>Monto:</td><td>&#36;15,000.00</td></tr>\
            <tr><td>Fecha:</td><td>01/11/2026</td></tr></table></body></html>";
        let text = html_to_text(body);
        let n = read_notice("BBVA <avisos@bbva.mx>", "SPEI recibido", &text, None).unwrap();
        assert_eq!(n.kind, NoticeKind::Income);
        // $15,000.00 = 15000×100 = 1500000
        assert_eq!(n.amount_cents, 1_500_000);
        assert_eq!(n.merchant.as_deref(), Some("EMPRESA EJEMPLO SA DE CV"));
        assert_eq!(n.card_last4.as_deref(), Some("4321"));
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-01"));
        // the text gave a date but no time: don't borrow one from the clock
        assert_eq!(n.occurred_time, None);
    }

    #[test]
    fn nu_purchase_from_subject_and_body() {
        let n = read_notice(
            "Nu México <hola@nu.com.mx>",
            "Compraste $89.90 en Spotify",
            "Hola, Ana:\nUsaste tu tarjeta Nu terminada en 5555 para una compra en Spotify por $89.90.\n¿No reconoces este cargo? Escríbenos.",
            Some(at("2026-11-04 22:10")),
        )
        .unwrap();
        assert_eq!(n.bank, Some("nu"));
        assert_eq!(n.kind, NoticeKind::Expense);
        // $89.90 = 89×100 + 90 = 8990
        assert_eq!(n.amount_cents, 8_990);
        assert_eq!(n.merchant.as_deref(), Some("Spotify"));
        assert_eq!(n.card_last4.as_deref(), Some("5555"));
        // no date in the text → the time it arrived
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-04"));
        assert_eq!(n.occurred_time.as_deref(), Some("22:10"));
    }

    #[test]
    fn nu_transfer_received() {
        let n = read_notice(
            "Nu <notificaciones@nu.com.mx>",
            "Recibiste una transferencia",
            "Recibiste $350.00 de María Gómez en tu cuenta Nu.\nYa puedes usar tu dinero.",
            Some(at("2026-11-03 08:00")),
        )
        .unwrap();
        assert_eq!(n.kind, NoticeKind::Income);
        // $350.00 = 35000
        assert_eq!(n.amount_cents, 35_000);
        assert_eq!(n.merchant.as_deref(), Some("María Gómez"));
    }

    #[test]
    fn nu_transfer_sent() {
        let n = read_notice(
            "Nu <notificaciones@nu.com.mx>",
            "Enviaste una transferencia",
            "Enviaste $1,000 a Carlos Ruiz desde tu cuenta Nu.",
            Some(at("2026-11-02 19:45")),
        )
        .unwrap();
        assert_eq!(n.kind, NoticeKind::Expense);
        // $1,000 (no decimals) = 1000×100 = 100000
        assert_eq!(n.amount_cents, 100_000);
        assert_eq!(n.merchant.as_deref(), Some("Carlos Ruiz"));
    }

    #[test]
    fn generic_bank_card_charge() {
        // A bank the reader has no profile for (sender added by the user).
        let n = read_notice(
            "Banorte <alertas@banorte.com>",
            "Alerta Banorte: cargo a tu tarjeta",
            "Se realizó un cargo por $ 450.50 en AMAZON MX con tu tarjeta XXXX1122 el 02/11/26 a las 7:15 p.m.",
            None,
        )
        .unwrap();
        assert_eq!(n.bank, None);
        assert_eq!(n.kind, NoticeKind::Expense);
        // $450.50 = 450×100 + 50 = 45050
        assert_eq!(n.amount_cents, 45_050);
        assert_eq!(n.merchant.as_deref(), Some("AMAZON MX"));
        assert_eq!(n.card_last4.as_deref(), Some("1122"));
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-02"));
        assert_eq!(n.occurred_time.as_deref(), Some("19:15"));
    }

    #[test]
    fn generic_usd_purchase() {
        let n = read_notice(
            "alertas@santander.com.mx",
            "Compra con tarjeta",
            "Compra aprobada por USD 12.99 en NETFLIX.COM tarjeta *7788",
            None,
        )
        .unwrap();
        assert_eq!(n.currency, "USD");
        // USD 12.99 = 1299 cents
        assert_eq!(n.amount_cents, 1_299);
        assert_eq!(n.merchant.as_deref(), Some("NETFLIX.COM"));
        assert_eq!(n.card_last4.as_deref(), Some("7788"));
    }

    #[test]
    fn pago_recibido_is_income() {
        let n = read_notice(
            "x@mercadopago.com.mx",
            "Pago recibido",
            "Te enviaron $200.00",
            None,
        )
        .unwrap();
        assert_eq!(n.kind, NoticeKind::Income);
        assert_eq!(n.amount_cents, 20_000);
    }

    #[test]
    fn balance_amount_is_not_the_movement() {
        let n = read_notice(
            "avisos@bbva.mx",
            "Retiro en cajero",
            "Tu saldo disponible es $5,000.00. Retiro de efectivo por $500.00 en cajero.",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 50_000);
    }

    #[test]
    fn credit_card_wording_is_not_a_balance() {
        // review of PR #2: "crédito" right after "tarjeta de" named the card
        // but was read as "saldo/crédito disponible", so the amount was lost.
        let n = read_notice(
            "hola@nu.com.mx",
            "Compra",
            "Compraste con tu tarjeta de crédito por $450.00 en OXXO",
            None,
        )
        .unwrap();
        // $450.00 = 45000
        assert_eq!(n.amount_cents, 45_000);
        assert_eq!(n.merchant.as_deref(), Some("OXXO"));

        let n = read_notice(
            "avisos@bbva.mx",
            "Cargo a tu tarjeta",
            "Se realizó un cargo a tu tarjeta de crédito por $2,000.00",
            None,
        )
        .unwrap();
        // $2,000.00 = 200000
        assert_eq!(n.amount_cents, 200_000);

        let n = read_notice(
            "avisos@bbva.mx",
            "Notificación de compra",
            "Compra con tu tarjeta de crédito terminación 1234 por $450.00 en OXXO",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 45_000);
        assert_eq!(n.card_last4.as_deref(), Some("1234"));

        // a real balance line still doesn't win over the movement
        let n = read_notice(
            "avisos@bbva.mx",
            "Compra",
            "Crédito disponible: $9,000.00. Compra por $100.00 en OXXO",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 10_000);
    }

    #[test]
    fn footer_mentioning_cancelled_card_keeps_the_charge() {
        let n = read_notice(
            "avisos@bbva.mx",
            "Notificación de compra",
            "Compra por $300.00 en AMAZON.\nSi tu tarjeta fue robada o cancelada, llámanos.",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 30_000);
        // …but a cancellation in the subject is not a movement
        assert_eq!(
            read_notice(
                "avisos@bbva.mx",
                "Compra cancelada",
                "Compra por $300.00",
                None
            ),
            Err(Rejection::NotAMovement)
        );
    }

    // ---- real notices (FIN-5) ----
    //
    // Text of real BBVA and Nu emails as the worker sees them once the HTML
    // is reduced to text (table cells flattened between `|`), with every
    // name, amount, account and reference replaced. Neither bank emails card
    // purchases (those are app pushes, FIN-1); these are what does arrive.

    const NU_TRANSFER_IN: &str = "Esta cantidad ya está disponible en tu cuenta\n\
        | Transferencia recibida |\n\
        | Hola, Ana: |\n\
        | JUAN PEREZ LOPEZ hizo una transferencia a tu Cuenta Nu por:* Monto: $1,150.00 * Fecha: 01 NOV 2026 * Hora: 21:55 Esta cantidad ya está disponible en tu cuenta. |\n\
        | Comparte Nu y aumenta las posibilidades de que alguien crezca su dinero 6.50% anual y disfrute los beneficios de la moradita. |\n\
        | Invitar ahora |\n\
        | Abrazos, Equipo Nu |\n\
        | Para emergencias, llama al 55 0000 0000. |";

    #[test]
    fn real_nu_transfer_received() {
        let n = read_notice(
            "Nu <nu@nu.com.mx>",
            "¡Recibiste una transferencia!",
            NU_TRANSFER_IN,
            None,
        )
        .unwrap();
        assert_eq!(n.bank, Some("nu"));
        assert_eq!(n.kind, NoticeKind::Income);
        // $1,150.00 = 1150×100 = 115000
        assert_eq!(n.amount_cents, 115_000);
        assert_eq!(n.merchant.as_deref(), Some("JUAN PEREZ LOPEZ"));
        assert_eq!(n.card_last4, None);
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-01"));
        assert_eq!(n.occurred_time.as_deref(), Some("21:55"));
    }

    #[test]
    fn real_nu_phone_top_up() {
        let body = "Tu comprobante de pago de recarga de celular\n\
            | Aquí está tu comprobante de tu pago de recarga de celular |\n\
            | 4 nov 2026 - 08:02:17 |\n\
            | Detalle Monto: $25.00 Costo extra: $0 Tipo de transacción: Recarga telefónica Número de celular: 5500000000 |\n\
            | Recibe Empresa a la cual se realizará el pago: Telcel Internet |\n\
            | Envía Nombre: Ana Ruiz Método de pago: Tarjeta de crédito Nu |\n\
            | Información adicional Código de operación: 00000aaa-1b11-2c22-3d33-444444444444 |\n\
            | Abrazos, Equipo Nu |";
        let n = read_notice(
            "nu@nu.com.mx",
            "Tu comprobante de pago de recarga de celular",
            body,
            None,
        )
        .unwrap();
        assert_eq!(n.kind, NoticeKind::Expense);
        // $25.00 = 2500; the "$0" extra fee after it is not the amount
        assert_eq!(n.amount_cents, 2_500);
        assert_eq!(n.merchant.as_deref(), Some("Telcel Internet"));
        assert_eq!(n.occurred_at.as_deref(), Some("2026-11-04"));
        assert_eq!(n.occurred_time.as_deref(), Some("08:02"));
    }

    #[test]
    fn real_bbva_spei_sent() {
        let body = " BBVA México\n\
            | Notificación BBVA |\n\
            | # Transferencia exitosa a cuenta de débito BBVA |\n\
            | Ruiz Pérez Ana, te compartimos los detalles de tu transferencia. |\n\
            | | Nombre del destinatario: JUAN PEREZ LOPEZ Monto: $ 700.00 Cuenta destino: 1234 |\n\
            | ## Detalles de operación |\n\
            | Concepto: | consulta |\n\
            | Fecha: | 23 de octubre de 2026 |\n\
            | Hora: | 21:10:34 h |\n\
            | Folio de internet: | 1000000000 |\n\
            | Saludos, Equipo BBVA. |\n\
            | ## ¿No reconoces esta operación? |\n\
            | Este correo electrónico constituye una notificación de los términos en que se realizó la operación, el único comprobante oficial es el estado de cuenta que emite BBVA. |";
        let n = read_notice(
            "Clientes BBVA <clientes@bbva.mx>",
            "La transferencia a cuenta de débito fue exitosa",
            body,
            None,
        )
        .unwrap();
        assert_eq!(n.bank, Some("bbva"));
        assert_eq!(n.kind, NoticeKind::Expense);
        // $ 700.00 = 70000
        assert_eq!(n.amount_cents, 70_000);
        assert_eq!(n.merchant.as_deref(), Some("JUAN PEREZ LOPEZ"));
        // "Cuenta destino" is the recipient's account, not the user's card
        assert_eq!(n.card_last4, None);
        assert_eq!(n.occurred_at.as_deref(), Some("2026-10-23"));
        assert_eq!(n.occurred_time.as_deref(), Some("21:10"));
    }

    #[test]
    fn real_own_transfers_are_set_apart() {
        // Nu: a payment to the user's own credit card
        assert_eq!(
            read_notice(
                "nu@nu.com.mx",
                "¡Recibimos tu pago!",
                "Ya lo puedes ver en tu app;\n| ¡Gracias por tu pago! |\n| Hola, Ana: |\n\
                 | Recibimos el pago que hiciste a tu tarjeta de crédito Nu por: |\n| $400.00 |\n\
                 | Esta cantidad ya está disponible en tu línea de crédito. |",
                None,
            ),
            Err(Rejection::OwnTransfer)
        );
        // BBVA: money taken out of a savings pocket into the account
        assert_eq!(
            read_notice(
                "clientes@bbva.mx",
                "Retiraste dinero de tus apartados",
                "| Notificación BBVA |\n| # Hola, ANA: |\n\
                 | El retiro de dinero de tu apartado en la cuenta terminación ******1234 fue exitoso, te compartimos el comprobante |\n\
                 | El dinero ya se encuentra en el saldo disponible de tu cuenta |\n\
                 | | Importe retirado: $4,000.00 |\n| Nombre del apartado: Viaje |\n| Fecha: 2026-10-19 |",
                None,
            ),
            Err(Rejection::OwnTransfer)
        );
    }

    #[test]
    fn footer_about_card_payments_keeps_the_charge() {
        // review 2 of PR #2: a footer reminder is not a payment to the card
        let n = read_notice(
            "avisos@bbva.mx",
            "Notificación de compra",
            "Compra por $300.00 en AMAZON. Recuerda domiciliar el pago a tu tarjeta de crédito.",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 30_000);
        assert_eq!(n.merchant.as_deref(), Some("AMAZON"));
    }

    #[test]
    fn abbreviations_are_not_sentence_ends() {
        // review 3 of PR #2: "p. m." with a space and "No. 1234"
        let n = read_notice(
            "alertas@banorte.com",
            "Cargo a tu tarjeta",
            "Cargo por $450.50 en AMAZON MX con tu tarjeta No. 1122 el 02/11/26 a las 7:15 p. m.",
            None,
        )
        .unwrap();
        assert_eq!(n.amount_cents, 45_050);
        assert_eq!(n.card_last4.as_deref(), Some("1122"));
        assert_eq!(n.occurred_time.as_deref(), Some("19:15"));
        assert_eq!(n.merchant.as_deref(), Some("AMAZON MX"));
    }

    #[test]
    fn nu_means_of_payment_is_not_the_merchant() {
        // "Método de pago:" before the real label must not win
        let n = read_notice(
            "nu@nu.com.mx",
            "Tu comprobante de pago de recarga de celular",
            "| Detalle Monto: $25.00 |\n| Envía Nombre: Ana Método de pago: Tarjeta de crédito Nu |\n\
             | Recibe Empresa a la cual se realizará el pago: Telcel Internet |",
            None,
        )
        .unwrap();
        assert_eq!(n.merchant.as_deref(), Some("Telcel Internet"));
    }

    #[test]
    fn real_non_movements_are_ignored() {
        // Nu: virtual card deleted (names a card ending and "pagos", no money)
        assert_eq!(
            read_notice(
                "nu@nu.com.mx",
                "Tarjeta virtual borrada",
                "Eliminaste tu tarjeta virtual\n| Hola, Ana, |\n\
                 | Te compartimos los sitios y apps en donde tenías registrada tu tarjeta virtual con terminación 1234 para que no tengas problemas con tus próximos pagos. |\n\
                 | Str*Telcel |\n\
                 | Para que los cobros recurrentes se sigan haciendo con normalidad, puedes crear una nueva tarjeta virtual y registrarla en los comercios. |",
                None,
            ),
            Err(Rejection::NotAMovement)
        );
        // BBVA marketing from email.bbva.mx with no promo word but "regalo"
        assert_eq!(
            read_notice(
                "BBVA <clientes@email.bbva.mx>",
                "$600 de regalo listos para El Buen Fin",
                "Ana, se acerca El Buen Fin y tu carrito de compras lo sabe. ¿Qué tal si nosotros ponemos los primeros $600 de la cuenta?\n\
                 Si traes tu nómina a BBVA, te regalamos un combo de bienvenida.\n$300 en tarjeta digital para Uber",
                None,
            ),
            Err(Rejection::Promotion)
        );
        // Nu: credit-limit offer
        assert_eq!(
            read_notice(
                "nu@nu.com.mx",
                "¡Tienes un aumento de límite de crédito preaprobado!",
                "Acepta tu nuevo límite en la app. Ahora puedes tener $20,000.00",
                None,
            ),
            Err(Rejection::Promotion)
        );
    }

    #[test]
    fn promotion_rejected() {
        assert_eq!(
            read_notice(
                "promociones@bbva.mx",
                "¡Aprovecha! Compra a meses sin intereses",
                "Compra hoy y paga $0 de intereses en compras desde $3,000.00",
                None
            ),
            Err(Rejection::Promotion)
        );
    }

    #[test]
    fn statement_and_declined_rejected() {
        assert_eq!(
            read_notice(
                "avisos@bbva.mx",
                "Tu estado de cuenta está listo",
                "Pago mínimo $1,200.00",
                None
            ),
            Err(Rejection::NotAMovement)
        );
        assert_eq!(
            read_notice(
                "hola@nu.com.mx",
                "Compra no aprobada",
                "Tu compra por $300.00 en AMAZON fue rechazada.",
                None
            ),
            Err(Rejection::NotAMovement)
        );
    }

    #[test]
    fn truncated_rejected() {
        assert_eq!(
            read_notice(
                "avisos@bbva.mx",
                "Notificación de compra",
                "Se realizó una compra con tu tarjeta en OXXO por",
                None
            ),
            Err(Rejection::NoAmount)
        );
    }

    #[test]
    fn unrelated_email_rejected() {
        assert_eq!(
            read_notice(
                "hola@nu.com.mx",
                "Bienvenida a Nu",
                "Gracias por unirte.",
                None
            ),
            Err(Rejection::NotAMovement)
        );
    }

    #[test]
    fn duplicate_window() {
        use NoticeKind::*;
        // same amount, 2 days apart → duplicate; 3 days → not
        assert!(looks_like_same_movement(
            Expense,
            8_990,
            "2026-11-04",
            "expense",
            8_990,
            "2026-11-06"
        ));
        assert!(!looks_like_same_movement(
            Expense,
            8_990,
            "2026-11-04",
            "expense",
            8_990,
            "2026-11-07"
        ));
        // a SPEI entered as a transfer leg counts
        assert!(looks_like_same_movement(
            Expense,
            250_000,
            "2026-11-06",
            "transfer_out",
            250_000,
            "2026-11-06"
        ));
        // opposite direction or different amount → not
        assert!(!looks_like_same_movement(
            Income,
            8_990,
            "2026-11-04",
            "expense",
            8_990,
            "2026-11-04"
        ));
        assert!(!looks_like_same_movement(
            Expense,
            8_990,
            "2026-11-04",
            "expense",
            8_991,
            "2026-11-04"
        ));
        assert!(!looks_like_same_movement(
            Expense,
            1,
            "bad",
            "expense",
            1,
            "2026-11-04"
        ));
    }

    #[test]
    fn sender_matching() {
        assert!(sender_matches("BBVA <avisos@bbva.mx>", "bbva.mx"));
        assert!(sender_matches("x@mail.bbva.mx", "bbva.mx"));
        assert!(sender_matches("x@mail.bbva.mx", "@bbva.mx"));
        assert!(!sender_matches("x@notbbva.mx", "bbva.mx"));
        assert!(sender_matches(
            "A <Alertas@Banorte.com>",
            "alertas@banorte.com"
        ));
        assert!(!sender_matches("otro@banorte.com", "alertas@banorte.com"));
        assert!(!sender_matches("x@bbva.mx", " "));
        assert_eq!(known_bank("hola@nu.com.mx").map(|b| b.key), Some("nu"));
        assert!(known_bank("x@banorte.com").is_none());
    }

    #[test]
    fn cents_parsing() {
        assert_eq!(parse_cents("1,234.56"), Some(123_456));
        assert_eq!(parse_cents("1234"), Some(123_400));
        assert_eq!(parse_cents("1.234,56"), Some(123_456));
        assert_eq!(parse_cents("0.99"), Some(99));
        assert_eq!(parse_cents("12,000"), Some(1_200_000));
        // one decimal digit: $12.5 = 12×100 + 50 = 1250, not $125.00
        assert_eq!(parse_cents("12.5"), Some(1_250));
    }

    #[test]
    fn html_entities_and_blocks() {
        let t = html_to_text("<div>Hola&nbsp;Ana</div><br>Monto:&#36;5.00 &amp; m&aacute;s");
        assert_eq!(t, "Hola Ana\nMonto:$5.00 & más");
    }
}
