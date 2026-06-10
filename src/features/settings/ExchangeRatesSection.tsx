import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, RefreshCw } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { inputClass } from "../../components/Field";
import {
  fetchExchangeRates,
  getExchangeRates,
  listCurrencies,
  setExchangeRate,
} from "../../lib/api";
import { es } from "../../i18n/es";

const MICROS = 1_000_000;

function RateRow({
  code,
  currentMicros,
  source,
}: {
  code: string;
  currentMicros: number | null;
  source: string | null;
}) {
  const queryClient = useQueryClient();
  const [text, setText] = useState(
    currentMicros !== null ? (currentMicros / MICROS).toFixed(4).replace(/\.?0+$/, "") : "",
  );
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (micros: number) => setExchangeRate(code, micros),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["exchangeRates"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (e) => setError(String(e)),
  });

  function submit() {
    setError(null);
    const value = parseFloat(text.replace(/,/g, ""));
    if (!Number.isFinite(value) || value <= 0) {
      setError(es.settings.invalidRate);
      return;
    }
    save.mutate(Math.round(value * MICROS));
  }

  return (
    <li className="flex items-center gap-3 py-2">
      <span className="w-12 font-mono text-sm font-medium text-accent">{code}</span>
      <input
        className={`${inputClass} w-36`}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={es.settings.noRateYet}
        inputMode="decimal"
      />
      <span className="text-xs text-zinc-500">MXN</span>
      {source && (
        <span className="rounded-full bg-surface-overlay px-2 py-0.5 text-xs text-zinc-500">
          {source === "api" ? es.settings.sourceApi : es.settings.sourceManual}
        </span>
      )}
      <Button
        variant="ghost"
        onClick={submit}
        disabled={save.isPending}
        className="ml-auto"
      >
        {save.isSuccess ? (
          <span className="flex items-center gap-1 text-accent">
            <Check size={14} /> {es.settings.rateUpdated}
          </span>
        ) : (
          es.common.save
        )}
      </Button>
      {error && <span className="text-xs text-danger">{error}</span>}
    </li>
  );
}

export function ExchangeRatesSection() {
  const queryClient = useQueryClient();
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const rates = useQuery({ queryKey: ["exchangeRates"], queryFn: getExchangeRates });

  const refresh = useMutation({
    mutationFn: fetchExchangeRates,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["exchangeRates"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const foreign = (currencies.data ?? []).filter((c) => c.code !== "MXN");
  if (foreign.length === 0) return null;

  const rateByCode = new Map((rates.data ?? []).map((r) => [r.currencyCode, r]));
  const sortedDates = (rates.data ?? []).map((r) => r.asOf).sort();
  const lastUpdate = sortedDates[sortedDates.length - 1];

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <div className="mb-1 flex items-center justify-between">
        <h3 className="font-medium">{es.settings.exchangeRates}</h3>
        <Button
          variant="ghost"
          onClick={() => refresh.mutate()}
          disabled={refresh.isPending}
        >
          <span className="flex items-center gap-2">
            <RefreshCw size={14} className={refresh.isPending ? "animate-spin" : ""} />
            {refresh.isPending ? es.settings.refreshing : es.settings.refreshRates}
          </span>
        </Button>
      </div>
      <p className="mb-1 text-xs text-zinc-500">{es.settings.exchangeRatesHint}</p>
      {lastUpdate && (
        <p className="mb-3 text-xs text-zinc-600">
          {es.settings.lastUpdate}: {lastUpdate}
        </p>
      )}
      {refresh.isError && (
        <p className="mb-2 text-xs text-danger">{es.settings.refreshError}</p>
      )}
      <ul className="divide-y divide-border-muted">
        {foreign.map((c) => {
          const rate = rateByCode.get(c.code);
          return (
            <RateRow
              key={`${c.code}-${rate?.rateToMxnMicros ?? "none"}-${rate?.asOf ?? ""}`}
              code={c.code}
              currentMicros={rate?.rateToMxnMicros ?? null}
              source={rate?.source ?? null}
            />
          );
        })}
      </ul>
    </section>
  );
}
