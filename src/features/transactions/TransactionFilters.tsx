import { useQuery } from "@tanstack/react-query";
import { inputClass } from "../../components/Field";
import { MultiSelect } from "../../components/MultiSelect";
import { PeriodPicker } from "../../components/PeriodPicker";
import { YieldToggle } from "../../components/YieldToggle";
import { listFilterCategories, listWallets } from "../../lib/api";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";
import type { Period, TransactionKind } from "../../lib/types";

export type FilterKind = TransactionKind | "transfer" | "";

interface TransactionFiltersProps {
  kind: FilterKind;
  categoryId: number | "";
  onChange: (next: { kind: FilterKind; categoryId: number | "" }) => void;
  /** Date window; null = every date, the default here. */
  period: Period | null;
  onPeriodChange: (p: Period | null) => void;
  /** Wallet picker — pass both to show it; omit when the wallet is fixed.
   *  Several can be picked; empty = every wallet. */
  walletIds?: number[];
  onWalletsChange?: (walletIds: number[]) => void;
  /** «Rendimientos» toggle — pass both to show it. Off hides the investments'
   *  daily yield and the wallets' automatic interest from the list. */
  showYield?: boolean;
  onShowYieldChange?: (show: boolean) => void;
}

/** Filter bar shared by the transactions tab and the wallet detail: type,
 *  category (only for income/expense/refund), date window and optionally the wallet. */
export function TransactionFilters({
  kind,
  categoryId,
  onChange,
  period,
  onPeriodChange,
  walletIds,
  onWalletsChange,
  showYield,
  onShowYieldChange,
}: TransactionFiltersProps) {
  const wallets = useQuery({
    queryKey: ["wallets", {}],
    queryFn: () => listWallets(),
    enabled: onWalletsChange !== undefined,
  });
  const categories = useQuery({
    queryKey: ["filterCategories"],
    queryFn: listFilterCategories,
  });

  return (
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
      {onWalletsChange && (
        <div className="w-full sm:w-56">
          <MultiSelect
            aria-label={es.transactions.walletsFilter}
            values={walletIds ?? []}
            onChange={onWalletsChange}
            options={(wallets.data ?? []).map((w) => ({ value: w.id, label: w.name }))}
            allLabel={es.transactions.allWallets}
            manyLabel={es.transactions.nWallets}
          />
        </div>
      )}
      {/* Type as segmented buttons; picking a kind also resets the category. */}
      <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
        {(
          [
            ["", es.transactions.typeAll],
            ["income", es.transactions.income],
            ["expense", es.transactions.expense],
            ["refund", es.transactions.refund],
            ["transfer", es.transactions.transfer],
          ] as const
        ).map(([val, label]) => (
          <button
            key={val || "all"}
            type="button"
            onClick={() => onChange({ kind: val, categoryId: "" })}
            className={`flex-1 whitespace-nowrap rounded-lg px-2 py-1.5 text-sm font-medium transition-colors sm:flex-none sm:px-3 ${
              kind === val
                ? "bg-surface-raised text-fg shadow-sm"
                : "text-fg-subtle hover:text-fg"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {/* Category only applies to income/expense/refund, scoped to the chosen
          kind (refunds use expense categories). */}
      {(kind === "income" || kind === "expense" || kind === "refund") && (
        <div className="w-full sm:w-56">
          <select
            className={inputClass}
            value={categoryId}
            onChange={(e) =>
              onChange({
                kind,
                categoryId: e.target.value === "" ? "" : Number(e.target.value),
              })
            }
          >
            <option value="">{es.transactions.allCategories}</option>
            {categories.data
              ?.filter((c) => c.kind === (kind === "refund" ? "expense" : kind))
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {seedName(c.name, c.isSystem)}
                </option>
              ))}
          </select>
        </div>
      )}
      {/* Date window, same picker (and same resolved windows) as the dashboard.
          "Todo el tiempo" here drops the date filter entirely rather than
          asking for the allTime window: this is a ledger, so a movement dated
          in the future has to stay visible. */}
      <PeriodPicker
        value={period ?? { kind: "allTime" }}
        onChange={(p) => onPeriodChange(p.kind === "allTime" ? null : p)}
        allowAll
      />
      {onShowYieldChange && <YieldToggle on={showYield ?? true} onChange={onShowYieldChange} />}
    </div>
  );
}
