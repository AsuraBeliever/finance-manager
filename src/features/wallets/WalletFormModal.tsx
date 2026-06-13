import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import {
  createWallet,
  listCurrencies,
  listWalletCategories,
  updateWallet,
  type WalletInput,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import type { Wallet } from "../../lib/types";
import { es } from "../../i18n/es";

const COLORS = CHART_COLORS;

interface WalletFormModalProps {
  open: boolean;
  onClose: () => void;
  /** When set, the form edits this wallet instead of creating one. */
  wallet?: Wallet;
}

export function WalletFormModal({ open, onClose, wallet }: WalletFormModalProps) {
  const queryClient = useQueryClient();
  const categories = useQuery({
    queryKey: ["walletCategories"],
    queryFn: listWalletCategories,
  });
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });

  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [currencyCode, setCurrencyCode] = useState("MXN");
  const [balanceText, setBalanceText] = useState("");
  const [color, setColor] = useState(COLORS[0]);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(wallet?.name ?? "");
    setCategoryId(wallet?.categoryId ?? null);
    setCurrencyCode(wallet?.currencyCode ?? "MXN");
    setBalanceText(
      wallet ? (wallet.initialBalanceCents / 100).toFixed(2) : "",
    );
    setColor(wallet?.color ?? COLORS[0]);
    setNotes(wallet?.notes ?? "");
    setError(null);
  }, [open, wallet]);

  const mutation = useMutation({
    mutationFn: (input: WalletInput) =>
      wallet ? updateWallet(wallet.id, input) : createWallet(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      onClose();
    },
    onError: (e) => setError(String(e)),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const cents = balanceText.trim() === "" ? 0 : parseToCents(balanceText);
    if (cents === null) {
      setError(es.wallets.invalidAmount);
      return;
    }
    const fallbackCategory = categories.data?.[0]?.id;
    const finalCategory = categoryId ?? fallbackCategory;
    if (finalCategory === undefined) return;
    mutation.mutate({
      name,
      categoryId: finalCategory,
      currencyCode,
      initialBalanceCents: cents,
      color,
      notes: notes.trim() === "" ? null : notes.trim(),
    });
  }

  return (
    <Modal
      title={wallet ? es.wallets.editWallet : es.wallets.newWallet}
      open={open}
      onClose={onClose}
    >
      <form onSubmit={submit} className="grid gap-4">
        <Field label={es.wallets.name}>
          <input
            className={inputClass}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={es.wallets.namePlaceholder}
            required
            autoFocus
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label={es.wallets.category}>
            <select
              className={inputClass}
              value={categoryId ?? categories.data?.[0]?.id ?? ""}
              onChange={(e) => setCategoryId(Number(e.target.value))}
            >
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label={es.wallets.currency}>
            <select
              className={inputClass}
              value={currencyCode}
              onChange={(e) => setCurrencyCode(e.target.value)}
            >
              {currencies.data?.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code} — {c.name}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <Field label={es.wallets.initialBalance}>
          <input
            className={inputClass}
            value={balanceText}
            onChange={(e) => setBalanceText(e.target.value)}
            placeholder="0.00"
            inputMode="decimal"
          />
        </Field>

        <Field label={es.wallets.color}>
          <div className="flex gap-2">
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setColor(c)}
                className={`h-7 w-7 rounded-full transition-transform ${
                  color === c ? "scale-110 ring-2 ring-stone-200" : ""
                }`}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>
        </Field>

        <Field label={es.wallets.notes}>
          <input
            className={inputClass}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Field>

        {error && <p className="text-sm text-danger">{error}</p>}
        {wallet && (
          <p className="text-xs text-stone-500">
            {es.wallets.balance}: {formatCents(wallet.balanceCents, wallet.currencyCode)}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {es.common.save}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
