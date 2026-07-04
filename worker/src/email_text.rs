//! Spanish rendering of notification kinds for the email digest. The bell
//! renders client-side from src/i18n (bilingual, follows the user's locale);
//! email has no client, so this mirrors the es-MX templates server-side.
//! Kind or params it doesn't recognize degrade to a generic line — never an
//! error, mirroring the frontend's fallback.

use serde_json::Value;

fn s<'a>(p: &'a Value, key: &str) -> &'a str {
    p.get(key).and_then(Value::as_str).unwrap_or("")
}

fn i(p: &Value, key: &str) -> Option<i64> {
    p.get(key).and_then(Value::as_i64)
}

/// Cents → "$1,234.56" (es-MX style), tagging non-MXN currencies.
fn money(p: &Value, key: &str, currency: &str) -> String {
    let cents = i(p, key).unwrap_or(0);
    let sign = if cents < 0 { "-" } else { "" };
    let abs = cents.unsigned_abs();
    let int = abs / 100;
    let frac = abs % 100;
    let mut grouped = String::new();
    for (n, ch) in int.to_string().chars().rev().enumerate() {
        if n > 0 && n % 3 == 0 {
            grouped.push(',');
        }
        grouped.push(ch);
    }
    let int_str: String = grouped.chars().rev().collect();
    let tag = if currency == "MXN" || currency.is_empty() {
        String::new()
    } else {
        format!(" {currency}")
    };
    format!("{sign}${int_str}.{frac:02}{tag}")
}

/// 'YYYY-MM-DD' → "5 jul" (mirrors the bell's short date).
fn date(p: &Value, key: &str) -> String {
    const MONTHS: [&str; 12] = [
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
    ];
    let raw = s(p, key);
    let mut parts = raw.split('-');
    let (Some(_), Some(m), Some(d)) = (parts.next(), parts.next(), parts.next()) else {
        return raw.to_string();
    };
    let month = m
        .parse::<usize>()
        .ok()
        .and_then(|n| MONTHS.get(n.wrapping_sub(1)))
        .copied()
        .unwrap_or("");
    format!("{} {month}", d.trim_start_matches('0'))
}

fn when(p: &Value) -> String {
    match i(p, "days") {
        Some(d) if d <= 0 => "hoy".into(),
        Some(1) => "mañana".into(),
        Some(d) => format!("en {d} días"),
        None => String::new(),
    }
}

fn period(p: &Value) -> &'static str {
    match s(p, "cadence") {
        "daily" => "hoy",
        "weekly" => "esta semana",
        "biweekly" => "esta quincena",
        "yearly" => "este año",
        _ => "este mes",
    }
}

fn esc(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

/// One alert as a Spanish sentence (HTML-escaped, ready for the digest body).
pub fn render_es(kind: &str, params_json: &str) -> String {
    let p: Value = serde_json::from_str(params_json).unwrap_or(Value::Null);
    let cur = s(&p, "currencyCode").to_string();
    let wallet = esc(s(&p, "wallet"));
    let name = esc(s(&p, "name"));
    match kind {
        "credit.cutSoon" => format!(
            "{wallet}: tu corte es {} ({}). Deuda actual: {}.",
            when(&p),
            date(&p, "date"),
            money(&p, "debtCents", &cur)
        ),
        "credit.dueSoon" => format!(
            "{wallet}: paga {} antes del {} ({}) para no generar intereses.",
            money(&p, "amountCents", &cur),
            date(&p, "date"),
            when(&p)
        ),
        "credit.utilization" => format!(
            "{wallet}: llevas {} % de tu línea de crédito usada.",
            i(&p, "utilizationBps").unwrap_or(0) / 100
        ),
        "credit.anniversary" => format!(
            "{wallet}: tu anualidad se cobra {} ({}).",
            when(&p),
            date(&p, "date")
        ),
        "credit.msiPosted" => format!(
            "{wallet}: se cargó tu mensualidad {} por {}.",
            esc(s(&p, "description")),
            money(&p, "amountCents", &cur)
        ),
        "goal.contribution" => format!(
            "«{name}»: aparta {} {} para llegar a tiempo.",
            money(&p, "amountCents", &cur),
            period(&p)
        ),
        "goal.behind" => format!(
            "«{name}»: vas {} por debajo del ritmo.",
            money(&p, "behindCents", &cur)
        ),
        "goal.deadlineSoon" => format!(
            "«{name}»: la fecha límite es {} ({}) y te faltan {}.",
            when(&p),
            date(&p, "date"),
            money(&p, "remainingCents", &cur)
        ),
        "goal.completed" => format!(
            "¡Meta cumplida! «{name}» llegó a {} 🎉",
            money(&p, "targetCents", &cur)
        ),
        "sub.chargeSoon" => format!(
            "{name}: se cobrará {} {} ({}).",
            money(&p, "amountCents", &cur),
            when(&p),
            date(&p, "date")
        ),
        "sub.chargeToday" => format!("{name}: hoy se cobra {}.", money(&p, "amountCents", &cur)),
        "inv.contribute" => format!("{name}: es tu día de aportar a esta inversión."),
        "inv.performance" if i(&p, "gainSinceCents").is_some() => format!(
            "{name}: llevas {}; generó {} desde el {} ({} en total).",
            money(&p, "valueCents", &cur),
            money(&p, "gainSinceCents", &cur),
            date(&p, "since"),
            money(&p, "totalGainCents", &cur)
        ),
        "inv.performance" | "inv.performanceFirst" => format!(
            "{name}: llevas {}; ha generado {} en total.",
            money(&p, "valueCents", &cur),
            money(&p, "totalGainCents", &cur)
        ),
        "inv.cetesMaturity" => format!("{name}: vence {} ({}).", when(&p), date(&p, "date")),
        _ => "Tienes un aviso nuevo.".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_the_due_soon_alert() {
        let params = r#"{"wallet":"BBVA Oro","amountCents":341250,"date":"2026-07-15","days":3,"currencyCode":"MXN"}"#;
        assert_eq!(
            render_es("credit.dueSoon", params),
            "BBVA Oro: paga $3,412.50 antes del 15 jul (en 3 días) para no generar intereses."
        );
    }

    #[test]
    fn renders_performance_with_and_without_delta() {
        let full = r#"{"name":"Cajita Nu","valueCents":6581831,"gainSinceCents":8720,"totalGainCents":1581831,"since":"2026-06-25","currencyCode":"MXN"}"#;
        assert_eq!(
            render_es("inv.performance", full),
            "Cajita Nu: llevas $65,818.31; generó $87.20 desde el 25 jun ($15,818.31 en total)."
        );
        let first = r#"{"name":"Cajita Nu","valueCents":6581831,"gainSinceCents":null,"totalGainCents":1581831,"currencyCode":"MXN"}"#;
        assert_eq!(
            render_es("inv.performance", first),
            "Cajita Nu: llevas $65,818.31; ha generado $15,818.31 en total."
        );
    }

    #[test]
    fn unknown_kind_degrades_gracefully() {
        assert_eq!(render_es("nope", "{"), "Tienes un aviso nuevo.");
    }

    #[test]
    fn escapes_html_in_names() {
        let params = r#"{"name":"<b>x</b>","amountCents":100,"currencyCode":"MXN"}"#;
        assert!(render_es("sub.chargeToday", params).contains("&lt;b&gt;x&lt;/b&gt;"));
    }
}
