import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import {
  createInvestment,
  fetchBanxicoRate,
  getInvestmentCatalog,
  listCurrencies,
  refreshMarketData,
  updateInvestment,
  type BanxicoSeriesKind,
  type CatalogItem,
  type InvestmentInput,
} from "../../lib/api";
import { formatBps } from "../../lib/money";
import { parseToCents } from "../../lib/money";
import type { CalculatorId, InvestmentWithValue } from "../../lib/types";
import { es } from "../../i18n/es";

const CALCULATORS: CalculatorId[] = [
  "nu_cajita",
  "cetes",
  "bonddia",
  "crypto",
  "fixed_rate",
  "manual",
];
const PLAZOS = [28, 91, 182, 364];
const COINS = ["BTC", "ETH", "SOL", "XRP", "DOGE", "ADA", "USDT", "USDC", "BNB", "LTC"];

/** Quantity text ("0.05") -> integer units ×1e8, or null when invalid. */
function parseQuantityE8(text: string): number | null {
  const cleaned = text.replace(/,/g, "").trim();
  if (!/^\d+(\.\d{1,8})?$/.test(cleaned)) return null;
  const value = Math.round(parseFloat(cleaned) * 1e8);
  return value > 0 ? value : null;
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Percent text ("12.5") -> basis points (1250), or null when invalid. */
function parseBps(text: string): number | null {
  const v = parseFloat(text.replace(/,/g, ""));
  if (!Number.isFinite(v) || v < 0) return null;
  return Math.round(v * 100);
}

interface InvestmentFormModalProps {
  open: boolean;
  onClose: () => void;
  investment?: InvestmentWithValue;
}

export function InvestmentFormModal({ open, onClose, investment }: InvestmentFormModalProps) {
  const queryClient = useQueryClient();
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });

  const [calculator, setCalculator] = useState<CalculatorId>("nu_cajita");
  const [name, setName] = useState("");
  const [principalText, setPrincipalText] = useState("");
  const [startDate, setStartDate] = useState(today());
  const [currencyCode, setCurrencyCode] = useState("MXN");
  const [rateText, setRateText] = useState("");
  const [plazo, setPlazo] = useState(91);
  const [isrText, setIsrText] = useState("0");
  const [reinvest, setReinvest] = useState(false);
  const [compounding, setCompounding] = useState("daily");
  const [symbol, setSymbol] = useState("BTC");
  const [quantityText, setQuantityText] = useState("");
  const [titulosText, setTitulosText] = useState("");
  const [remanentesText, setRemanentesText] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [rateInfo, setRateInfo] = useState<string | null>(null);
  const [step, setStep] = useState<"catalog" | "form">("catalog");

  const catalog = useQuery({
    queryKey: ["investmentCatalog"],
    queryFn: getInvestmentCatalog,
    enabled: open && !investment,
    staleTime: 10 * 60 * 1000,
  });

  function pickCatalogItem(item: CatalogItem) {
    const params = JSON.parse(item.paramsJson);
    setCalculator(item.calculator);
    setName(es.investments.catalog[item.id]?.name ?? "");
    setRateText(
      params.annual_rate_bps !== undefined ? (params.annual_rate_bps / 100).toString() : "",
    );
    setPlazo(params.plazo_days ?? 91);
    setIsrText(params.isr_rate_bps !== undefined ? (params.isr_rate_bps / 100).toString() : "0");
    setReinvest(params.reinvest ?? false);
    setCompounding(params.compounding ?? "daily");
    setSymbol(params.symbol ?? "BTC");
    setQuantityText("");
    setTitulosText("");
    setRemanentesText("");
    setRateInfo(
      item.rateBps !== null && item.rateDate
        ? `${es.investments.banxicoFetched} ${item.rateDate}`
        : null,
    );
    setStep("form");
  }

  // Nu sets its own rate; Banxico only helps for CETES (auction by plazo)
  // and BONDDIA-style funds (target rate as reference).
  const banxicoKind: BanxicoSeriesKind | null =
    calculator === "cetes"
      ? (`cetes_${plazo}` as BanxicoSeriesKind)
      : calculator === "fixed_rate"
        ? "objetivo"
        : null;

  const banxico = useMutation({
    mutationFn: () => fetchBanxicoRate(banxicoKind!),
    onSuccess: (r) => {
      setRateText((r.rateBps / 100).toString());
      setRateInfo(`${es.investments.banxicoFetched} ${r.date}`);
      setError(null);
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  useEffect(() => {
    if (!open) return;
    const params = investment ? JSON.parse(investment.paramsJson) : {};
    setCalculator(investment?.calculator ?? "nu_cajita");
    setName(investment?.name ?? "");
    setPrincipalText(investment ? (investment.principalCents / 100).toFixed(2) : "");
    setStartDate(investment?.startDate ?? today());
    setCurrencyCode(investment?.currencyCode ?? "MXN");
    setRateText(
      params.annual_rate_bps !== undefined ? (params.annual_rate_bps / 100).toString() : "",
    );
    setPlazo(params.plazo_days ?? 91);
    setIsrText(params.isr_rate_bps !== undefined ? (params.isr_rate_bps / 100).toString() : "0");
    setReinvest(params.reinvest ?? false);
    setCompounding(params.compounding ?? "daily");
    setSymbol(params.symbol ?? "BTC");
    setQuantityText(
      params.quantity_e8 !== undefined ? (params.quantity_e8 / 1e8).toString() : "",
    );
    setTitulosText(params.titulos !== undefined ? String(params.titulos) : "");
    setRemanentesText(
      params.remanentes_cents !== undefined
        ? (params.remanentes_cents / 100).toFixed(2)
        : "",
    );
    setNotes(investment?.notes ?? "");
    setError(null);
    setRateInfo(null);
    setStep(investment ? "form" : "catalog");
  }, [open, investment]);

  // bonddia uses the cached historical series; crypto uses live prices.
  const needsRate = !["manual", "crypto", "bonddia"].includes(calculator);

  const mutation = useMutation({
    mutationFn: async () => {
      const principal = parseToCents(principalText);
      if (principal === null || principal <= 0)
        throw new Error(es.investments.invalidAmount);

      const params: Record<string, unknown> = {};
      if (needsRate) {
        const bps = parseBps(rateText);
        if (bps === null) throw new Error(es.investments.invalidRate);
        params.annual_rate_bps = bps;
      }
      if (calculator === "cetes") {
        const isr = parseBps(isrText);
        if (isr === null) throw new Error(es.investments.invalidRate);
        params.plazo_days = plazo;
        params.isr_rate_bps = isr;
        params.reinvest = reinvest;
      }
      if (calculator === "fixed_rate") {
        params.compounding = compounding;
      }
      if (calculator === "bonddia") {
        // keep the catalog's live rate + calibrated spread as estimation params
        const existing = investment ? JSON.parse(investment.paramsJson) : null;
        const fallback = existing?.annual_rate_bps ?? parseBps(rateText);
        if (fallback) params.annual_rate_bps = fallback;
        if (existing?.spread_bps !== undefined) params.spread_bps = existing.spread_bps;
        if (titulosText.trim() !== "") {
          const titulos = Number(titulosText.replace(/[,\s]/g, ""));
          if (!Number.isInteger(titulos) || titulos <= 0)
            throw new Error(es.investments.invalidQuantity);
          params.titulos = titulos;
          const rem = remanentesText.trim() === "" ? 0 : parseToCents(remanentesText);
          if (rem === null || rem < 0) throw new Error(es.investments.invalidAmount);
          params.remanentes_cents = rem;
        }
      }
      if (calculator === "crypto") {
        const qty = parseQuantityE8(quantityText);
        if (qty === null) throw new Error(es.investments.invalidQuantity);
        params.symbol = symbol;
        params.quantity_e8 = qty;
      }

      const input: InvestmentInput = {
        name,
        currencyCode,
        principalCents: principal,
        startDate,
        paramsJson: JSON.stringify(params),
        linkedWalletId: null,
        notes: notes.trim() === "" ? null : notes.trim(),
      };
      return investment
        ? updateInvestment(investment.id, input)
        : createInvestment(calculator, input);
    },
    onSuccess: async () => {
      if (calculator === "crypto") {
        // pull the price for the new symbol right away, then refresh views
        try {
          await refreshMarketData();
        } catch {
          // offline: value falls back to what was paid until a price arrives
        }
      }
      queryClient.invalidateQueries({ queryKey: ["investments"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal
      title={
        investment
          ? es.investments.editInvestment
          : step === "catalog"
            ? es.investments.catalogTitle
            : es.investments.newInvestment
      }
      open={open}
      onClose={onClose}
    >
      {step === "catalog" && !investment ? (
        // minmax(0,1fr): otherwise WebKitGTK sizes the track to the widest
        // nowrap description and every row overflows the modal
        <div className="grid grid-cols-[minmax(0,1fr)] gap-2">
          {catalog.isPending && (
            <p className="py-4 text-center text-sm text-stone-500">
              {es.investments.catalogLoading}
            </p>
          )}
          {catalog.data?.map((item) => {
            const label = es.investments.catalog[item.id];
            if (!label) return null;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => pickCatalogItem(item)}
                className="flex w-full min-w-0 items-center gap-3 rounded-xl border border-border-muted bg-surface px-4 py-3 text-left transition-colors hover:border-accent-dim/60"
              >
                <span className="min-w-0 flex-1">
                  <span className="block font-medium">{label.name}</span>
                  <span className="block truncate text-xs text-stone-500">
                    {label.description}
                  </span>
                </span>
                {item.rateBps !== null ? (
                  <span className="shrink-0 text-right">
                    <span className="block font-semibold tabular-nums text-accent">
                      {formatBps(item.rateBps)}
                    </span>
                    {item.rateDate && (
                      <span className="block text-xs text-stone-600">
                        {es.investments.catalogRateAsOf} {item.rateDate}
                      </span>
                    )}
                  </span>
                ) : ["nu_cajita", "fixed_rate"].includes(item.id) ? (
                  <span className="text-xs text-stone-500">
                    {es.investments.catalogNoRate}
                  </span>
                ) : null}
              </button>
            );
          })}
        </div>
      ) : (
      <form
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
        className="grid gap-4"
      >
        {investment ? (
          <Field label={es.investments.calculator}>
            <select className={inputClass} value={calculator} disabled>
              {CALCULATORS.map((c) => (
                <option key={c} value={c}>
                  {es.investments.calculators[c]}
                </option>
              ))}
            </select>
          </Field>
        ) : (
          <button
            type="button"
            onClick={() => setStep("catalog")}
            className="justify-self-start text-sm text-accent hover:underline"
          >
            {es.investments.catalogBack}
          </button>
        )}

        <Field label={es.investments.name}>
          <input
            className={inputClass}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={es.investments.namePlaceholder}
            required
            autoFocus
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label={es.investments.principal}>
            <input
              className={inputClass}
              value={principalText}
              onChange={(e) => setPrincipalText(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              required
            />
          </Field>
          <Field label={es.investments.startDate}>
            <DateInput value={startDate} onChange={setStartDate} />
          </Field>
        </div>
        {calculator === "crypto" && (
          <p className="-mt-2 text-xs text-stone-500">
            {es.investments.cryptoPrincipalHint}
          </p>
        )}

        <div className="grid grid-cols-2 gap-3">
          <Field label={es.investments.currency}>
            <select
              className={inputClass}
              value={currencyCode}
              onChange={(e) => setCurrencyCode(e.target.value)}
            >
              {currencies.data?.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code}
                </option>
              ))}
            </select>
          </Field>
          {needsRate && (
            <Field label={es.investments.annualRate}>
              <input
                className={inputClass}
                value={rateText}
                onChange={(e) => setRateText(e.target.value)}
                placeholder="15.0"
                inputMode="decimal"
                required
              />
            </Field>
          )}
        </div>

        {banxicoKind && (
          <div className="-mt-2">
            <button
              type="button"
              onClick={() => banxico.mutate()}
              disabled={banxico.isPending}
              className="flex items-center gap-2 text-sm text-accent hover:underline disabled:opacity-50"
            >
              <RefreshCw size={13} className={banxico.isPending ? "animate-spin" : ""} />
              {calculator === "cetes"
                ? `${es.investments.banxicoCetes} (${plazo} días)`
                : es.investments.banxicoObjetivo}
            </button>
            {rateInfo && <p className="mt-1 text-xs text-stone-500">{rateInfo}</p>}
          </div>
        )}

        {calculator === "cetes" && (
          <div className="grid grid-cols-2 gap-3">
            <Field label={es.investments.plazo}>
              <select
                className={inputClass}
                value={plazo}
                onChange={(e) => setPlazo(Number(e.target.value))}
              >
                {PLAZOS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={es.investments.isrRate}>
              <input
                className={inputClass}
                value={isrText}
                onChange={(e) => setIsrText(e.target.value)}
                inputMode="decimal"
              />
              <span className="mt-1 block text-xs text-stone-500">
                {es.investments.isrHint}
              </span>
            </Field>
          </div>
        )}

        {calculator === "bonddia" && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <Field label={es.investments.bonddiaTitulos}>
                <input
                  className={inputClass}
                  value={titulosText}
                  onChange={(e) => setTitulosText(e.target.value)}
                  placeholder="2923"
                  inputMode="numeric"
                />
              </Field>
              <Field label={es.investments.bonddiaRemanentes}>
                <input
                  className={inputClass}
                  value={remanentesText}
                  onChange={(e) => setRemanentesText(e.target.value)}
                  placeholder="0.00"
                  inputMode="decimal"
                />
              </Field>
            </div>
            <p className="-mt-2 text-xs text-stone-500">{es.investments.bonddiaHint}</p>
          </>
        )}

        {calculator === "crypto" && (
          <div className="grid grid-cols-2 gap-3">
            <Field label={es.investments.cryptoSymbol}>
              <select
                className={inputClass}
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
              >
                {COINS.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={es.investments.cryptoQuantity}>
              <input
                className={inputClass}
                value={quantityText}
                onChange={(e) => setQuantityText(e.target.value)}
                placeholder="0.05"
                inputMode="decimal"
                required
              />
            </Field>
          </div>
        )}

        {calculator === "cetes" && (
          <label className="flex cursor-pointer items-start gap-3">
            <input
              type="checkbox"
              checked={reinvest}
              onChange={(e) => setReinvest(e.target.checked)}
              className="mt-1 accent-accent"
            />
            <span>
              <span className="block text-sm font-medium">{es.investments.reinvest}</span>
              <span className="block text-xs text-stone-500">{es.investments.reinvestHint}</span>
            </span>
          </label>
        )}

        {calculator === "fixed_rate" && (
          <Field label={es.investments.compounding}>
            <select
              className={inputClass}
              value={compounding}
              onChange={(e) => setCompounding(e.target.value)}
            >
              {Object.entries(es.investments.compoundingOptions).map(([id, label]) => (
                <option key={id} value={id}>
                  {label}
                </option>
              ))}
            </select>
          </Field>
        )}

        <Field label={es.investments.notes}>
          <input className={inputClass} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {es.common.save}
          </Button>
        </div>
      </form>
      )}
    </Modal>
  );
}
