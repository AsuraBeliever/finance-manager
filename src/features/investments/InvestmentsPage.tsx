import { useQuery } from "@tanstack/react-query";
import { Calculator, Plus, TrendingDown, TrendingUp } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { listInvestments } from "../../lib/api";
import { formatCents } from "../../lib/money";
import { es } from "../../i18n/es";
import { InvestmentFormModal } from "./InvestmentFormModal";
import { PortfolioSummary } from "./PortfolioSummary";

/** "0.05 BTC" from a crypto investment's params. */
function cryptoSub(paramsJson: string): string {
  try {
    const p = JSON.parse(paramsJson);
    return `${(p.quantity_e8 / 1e8).toString()} ${p.symbol}`;
  } catch {
    return es.investments.calculators.crypto;
  }
}

export function InvestmentsPage() {
  const [showClosed, setShowClosed] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const investments = useQuery({
    queryKey: ["investments", { showClosed }],
    queryFn: () => listInvestments(showClosed),
  });

  const items = investments.data ?? [];

  return (
    <>
      <PageHeader
        title={es.investments.title}
        actions={
          <div className="flex items-center gap-4">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-fg-muted">
              <input
                type="checkbox"
                checked={showClosed}
                onChange={(e) => setShowClosed(e.target.checked)}
                className="accent-accent"
              />
              {es.investments.showClosed}
            </label>
            <Link
              to="/inversiones/simulador"
              className="inline-flex items-center gap-2 rounded-lg border border-border-muted px-3 py-2 text-sm font-medium text-fg-muted transition-colors hover:border-accent hover:text-accent"
            >
              <Calculator size={16} /> {es.simulator.open}
            </Link>
            <Button onClick={() => setFormOpen(true)}>
              <span className="flex items-center gap-2">
                <Plus size={16} /> {es.investments.newInvestment}
              </span>
            </Button>
          </div>
        }
      />

      {investments.isError && (
        <p className="text-sm text-danger">{String(investments.error)}</p>
      )}

      {investments.isSuccess && items.length === 0 && (
        <EmptyState
          icon={TrendingUp}
          title={es.investments.emptyTitle}
          description={es.investments.emptyDescription}
        />
      )}

      {items.length > 0 && <PortfolioSummary />}

      <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
        {items.map((inv) => {
          const gainPositive = inv.gainCents >= 0;
          return (
            <Link
              key={inv.id}
              to={`/inversiones/${inv.id}`}
              className="rounded-xl border border-border-muted bg-surface-raised p-4 transition-colors hover:border-accent-dim/60"
            >
              <div className="mb-2 flex items-center gap-2">
                <span className="truncate font-medium">{inv.name}</span>
                {inv.isClosed && (
                  <span className="ml-auto rounded-full bg-surface-overlay px-2 py-0.5 text-xs text-fg-subtle">
                    {es.investments.closed}
                  </span>
                )}
              </div>
              <p className="text-xl font-semibold tabular-nums">
                {formatCents(inv.currentValueCents, inv.currencyCode)}
              </p>
              <p
                className={`mt-1 flex items-center gap-1 text-sm tabular-nums ${
                  gainPositive ? "text-accent" : "text-danger"
                }`}
              >
                {gainPositive ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
                {gainPositive ? "+" : ""}
                {formatCents(inv.gainCents, inv.currencyCode)}
              </p>
              <p className="mt-2 text-xs text-fg-subtle">
                {inv.calculator === "crypto"
                  ? cryptoSub(inv.paramsJson)
                  : (es.investments.calculators[inv.calculator] ?? inv.calculator)}
                {inv.maturityDate && (
                  <>
                    {" · "}
                    {es.investments.maturity}: {inv.maturityDate}
                  </>
                )}
              </p>
            </Link>
          );
        })}
      </div>

      <InvestmentFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
