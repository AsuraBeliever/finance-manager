import { useQuery } from "@tanstack/react-query";
import { inputClass } from "../../components/Field";
import { PeriodPicker } from "../../components/PeriodPicker";
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
  /** Wallet picker — pass both to show it; omit when the wallet is fixed. */
  walletId?: number | "";
  onWalletChange?: (walletId: number | "") => void;
}

/** Filter bar shared by the transactions tab and the wallet detail: type,
 *  category (only for income/expense), date window and optionally the wallet. */
export function TransactionFilters({
  kind,
  categoryId,
  onChange,
  period,
  onPeriodChange,
  walletId,
  onWalletChange,
}: TransactionFiltersProps) {
  const wallets = useQuery({
    queryKey: ["wallets", {}],
    queryFn: () => listWallets(),
    enabled: onWalletChange !== undefined,
  });
  const categories = useQuery({
    queryKey: ["filterCategories"],
    queryFn: listFilterCategories,
  });

  return (
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
      {onWalletChange && (
        <div className="w-full sm:w-56">
          <select
            className={inputClass}
            value={walletId ?? ""}
            onChange={(e) =>
              onWalletChange(e.target.value === "" ? "" : Number(e.target.value))
            }
          >
            <option value="">{es.transactions.allWallets}</option>
            {wallets.data?.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {/* Type as segmented buttons; picking a kind also resets the category. */}
      <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
        {(
          [
            ["", es.transactions.typeAll],
            ["income", es.transactions.income],
            ["expense", es.transactions.expense],
            ["transfer", es.transactions.transfer],
          ] as const
        ).map(([val, label]) => (
          <button
            key={val || "all"}
            type="button"
            onClick={() => onChange({ kind: val, categoryId: "" })}
            className={`flex-1 whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition-colors sm:flex-none ${
              kind === val
                ? "bg-surface-raised text-fg shadow-sm"
                : "text-fg-subtle hover:text-fg"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {/* Category only applies to income/expense, scoped to the chosen kind. */}
      {(kind === "income" || kind === "expense") && (
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
              ?.filter((c) => c.kind === kind)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {seedName(c.name, c.isSystem)}
                </option>
              ))}
          </select>
        </div>
      )}
      {/* Date window, same picker (and same resolved windows) as the dashboard,
          plus "todo el tiempo" — the default for a history list. */}
      <PeriodPicker value={period} onChange={onPeriodChange} allowAll />
    </div>
  );
}
