import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import {
  getInvestmentMovement,
  listWallets,
  updateInvestmentMovement,
} from "../../lib/api";
import { parseToCents } from "../../lib/money";
import { es } from "../../i18n/es";

interface MovementEditModalProps {
  open: boolean;
  onClose: () => void;
  /** The movement to edit, addressed either by its own id (investment page) or
   *  by its wallet transfer leg (transactions list). */
  movementId?: number;
  transactionId?: number;
}

/** Fixes a contribution/withdrawal already recorded: amount, date, wallet, or
 *  deposit vs withdrawal. The server moves the investment and the wallet leg
 *  together, so both always agree. */
export function MovementEditModal({
  open,
  onClose,
  movementId,
  transactionId,
}: MovementEditModalProps) {
  const queryClient = useQueryClient();
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const movement = useQuery({
    queryKey: ["investmentMovement", movementId ?? null, transactionId ?? null],
    queryFn: () => getInvestmentMovement({ id: movementId, transactionId }),
    enabled: open && (movementId !== undefined || transactionId !== undefined),
  });

  const [kind, setKind] = useState<"deposit" | "withdrawal">("deposit");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState("");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Prefill once the movement loads (and whenever a different one is opened).
  const loaded = movement.data;
  useEffect(() => {
    if (!open || !loaded) return;
    setKind(loaded.kind);
    setAmount((loaded.amountCents / 100).toFixed(2));
    setDate(loaded.occurredAt);
    setWalletId(loaded.walletId);
    setError(null);
  }, [open, loaded]);

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(amount);
      if (cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      return updateInvestmentMovement({
        id: loaded!.id,
        kind,
        amountCents: cents,
        occurredAt: date,
        walletId,
      });
    },
    onSuccess: () => {
      // The edit can touch the investment, a wallet balance and the ledger.
      for (const key of [
        "investments",
        "investment",
        "investmentMovement",
        "wallets",
        "transactions",
        "dashboard",
        "portfolio",
      ])
        queryClient.invalidateQueries({ queryKey: [key] });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal title={es.investments.movementEditTitle} open={open} onClose={onClose}>
      {!loaded ? (
        <p className="py-2 text-sm text-fg-subtle">{es.common.loading}</p>
      ) : (
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            save.mutate();
          }}
        >
          <p className="text-sm text-fg-muted">{loaded.investmentName}</p>
          <Field label={es.investments.movementKind}>
            <select
              className={inputClass}
              value={kind}
              onChange={(e) => setKind(e.target.value as "deposit" | "withdrawal")}
            >
              <option value="deposit">{es.investments.depositNoun}</option>
              <option value="withdrawal">{es.investments.withdrawalNoun}</option>
            </select>
          </Field>
          <Field label={es.investments.movementAmount}>
            <MoneyInput value={amount} onChange={setAmount} required autoFocus />
          </Field>
          <Field label={es.investments.movementDate}>
            <DateInput value={date} onChange={setDate} min={loaded.startDate} />
          </Field>
          <Field label={es.investments.movementWallet}>
            <select
              className={inputClass}
              value={walletId ?? ""}
              onChange={(e) => setWalletId(e.target.value === "" ? null : Number(e.target.value))}
            >
              <option value="">{es.investments.movementWalletNone}</option>
              {wallets.data?.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name} ({w.currencyCode})
                </option>
              ))}
            </select>
            {walletId !== null && (
              <span className="mt-1 block text-xs text-fg-subtle">
                {kind === "withdrawal"
                  ? es.investments.movementWalletWithdrawalHint
                  : es.investments.movementWalletDepositHint}
              </span>
            )}
          </Field>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose}>
              {es.common.cancel}
            </Button>
            <Button type="submit" variant="primary" disabled={save.isPending}>
              {es.common.save}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
