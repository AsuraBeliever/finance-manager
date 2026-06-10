import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { inputClass } from "../../components/Field";
import { getExchangeRates, listCurrencies, setExchangeRate } from "../../lib/api";
import { es } from "../../i18n/es";

const MICROS = 1_000_000;

function RateRow({ code, currentMicros }: { code: string; currentMicros: number | null }) {
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
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const rates = useQuery({ queryKey: ["exchangeRates"], queryFn: getExchangeRates });

  const foreign = (currencies.data ?? []).filter((c) => c.code !== "MXN");
  if (foreign.length === 0) return null;

  const rateByCode = new Map(
    (rates.data ?? []).map((r) => [r.currencyCode, r.rateToMxnMicros]),
  );

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 font-medium">{es.settings.exchangeRates}</h3>
      <p className="mb-3 text-xs text-zinc-500">{es.settings.exchangeRatesHint}</p>
      <ul className="divide-y divide-border-muted">
        {foreign.map((c) => (
          <RateRow
            key={`${c.code}-${rateByCode.get(c.code) ?? "none"}`}
            code={c.code}
            currentMicros={rateByCode.get(c.code) ?? null}
          />
        ))}
      </ul>
    </section>
  );
}
