import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "../../components/PageHeader";
import { listCurrencies, listWalletCategories } from "../../lib/api";
import { es } from "../../i18n/es";
import { BanxicoSection } from "./BanxicoSection";
import { ExchangeRatesSection } from "./ExchangeRatesSection";

export function SettingsPage() {
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const categories = useQuery({
    queryKey: ["walletCategories"],
    queryFn: listWalletCategories,
  });

  return (
    <>
      <PageHeader title={es.settings.title} />
      <div className="grid max-w-3xl gap-6">
        <ExchangeRatesSection />
        <BanxicoSection />
        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 font-medium">{es.settings.currencies}</h3>
          {currencies.isPending && (
            <p className="text-sm text-zinc-500">{es.common.loading}</p>
          )}
          {currencies.isError && (
            <p className="text-sm text-danger">{String(currencies.error)}</p>
          )}
          <ul className="divide-y divide-border-muted">
            {currencies.data?.map((c) => (
              <li key={c.code} className="flex items-center gap-3 py-2 text-sm">
                <span className="w-12 font-mono font-medium text-accent">{c.code}</span>
                <span className="text-zinc-300">{c.name}</span>
                <span className="ml-auto text-zinc-500">{c.symbol}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 font-medium">{es.settings.walletCategories}</h3>
          <ul className="flex flex-wrap gap-2">
            {categories.data?.map((c) => (
              <li
                key={c.id}
                className="rounded-full bg-surface-overlay px-3 py-1 text-sm text-zinc-300"
              >
                {c.name}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}
