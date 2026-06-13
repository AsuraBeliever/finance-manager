import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Check, Upload } from "lucide-react";
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
import { resolveSkin, SKINS, type SkinGroup } from "../../lib/skins";
import type { Wallet } from "../../lib/types";
import { es } from "../../i18n/es";

const COLORS = CHART_COLORS;

const SKIN_GROUPS: { key: SkinGroup; label: string }[] = [
  { key: "banco", label: es.wallets.skinGroupBanco },
  { key: "nivel", label: es.wallets.skinGroupNivel },
  { key: "efectivo", label: es.wallets.skinGroupEfectivo },
  { key: "glass", label: es.wallets.skinGroupGlass },
];

/** Downscale + JPEG-compress an uploaded image to a small data URL. */
async function compressImage(file: File): Promise<string> {
  const bmp = await createImageBitmap(file);
  const maxW = 720;
  const scale = Math.min(1, maxW / bmp.width);
  const w = Math.round(bmp.width * scale);
  const h = Math.round(bmp.height * scale);
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  canvas.getContext("2d")!.drawImage(bmp, 0, 0, w, h);
  return canvas.toDataURL("image/jpeg", 0.72);
}

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
  const [skin, setSkin] = useState<string | null>(null);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    setName(wallet?.name ?? "");
    setCategoryId(wallet?.categoryId ?? null);
    setCurrencyCode(wallet?.currencyCode ?? "MXN");
    setBalanceText(wallet ? (wallet.initialBalanceCents / 100).toFixed(2) : "");
    setColor(wallet?.color ?? COLORS[0]);
    setSkin(wallet?.skin ?? null);
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

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (!f) return;
    try {
      setSkin("img:" + (await compressImage(f)));
    } catch {
      setError(es.wallets.invalidAmount);
    }
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const cents = balanceText.trim() === "" ? 0 : parseToCents(balanceText);
    if (cents === null) {
      setError(es.wallets.invalidAmount);
      return;
    }
    const finalCategory = categoryId ?? categories.data?.[0]?.id;
    if (finalCategory === undefined) return;
    mutation.mutate({
      name,
      categoryId: finalCategory,
      currencyCode,
      initialBalanceCents: cents,
      color,
      skin,
      notes: notes.trim() === "" ? null : notes.trim(),
    });
  }

  const imgSelected = !!skin && skin.startsWith("img:");
  const autoBg = resolveSkin(null, color).background;

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

        {/* Card skin picker */}
        <Field label={es.wallets.skin}>
          <div className="space-y-3">
            {/* Auto + import row */}
            <div className="flex flex-wrap items-center gap-2">
              <SkinSwatch
                background={autoBg}
                label={es.wallets.skinAuto}
                selected={!skin}
                onClick={() => setSkin(null)}
              />
              {imgSelected && (
                <SkinSwatch background={resolveSkin(skin, color).background} selected onClick={() => {}} />
              )}
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="flex h-11 items-center gap-1.5 rounded-lg border border-dashed border-border-muted px-3 text-xs text-fg-muted transition-colors hover:border-accent hover:text-fg"
              >
                <Upload size={14} />
                {es.wallets.skinImport}
              </button>
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFile}
              />
            </div>

            {SKIN_GROUPS.map((g) => {
              const items = SKINS.filter((s) => s.group === g.key);
              if (items.length === 0) return null;
              return (
                <div key={g.key}>
                  <p className="mb-1.5 text-[0.65rem] font-medium uppercase tracking-[0.12em] text-fg-subtle">
                    {g.label}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {items.map((s) => (
                      <SkinSwatch
                        key={s.id}
                        background={s.background}
                        label={s.label}
                        selected={skin === s.id}
                        onClick={() => setSkin(s.id)}
                      />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </Field>

        <Field label={es.wallets.color}>
          <div className="flex flex-wrap gap-2">
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setColor(c)}
                className={`h-7 w-7 rounded-full transition-transform ${
                  color === c ? "scale-110 ring-2 ring-fg" : ""
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
          <p className="text-xs text-fg-subtle">
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

function SkinSwatch({
  background,
  label,
  selected,
  onClick,
}: {
  background: string;
  label?: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      onClick={onClick}
      className={`relative h-11 w-[4.5rem] overflow-hidden rounded-lg ring-1 ring-inset ring-white/15 transition-transform ${
        selected ? "scale-105 outline outline-2 outline-accent" : "hover:scale-105"
      }`}
      style={{ background }}
    >
      <span
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "linear-gradient(120deg, rgba(255,255,255,0.35) 0%, transparent 45%)",
        }}
      />
      {selected && (
        <span className="absolute inset-0 flex items-center justify-center">
          <Check size={16} className="text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.6)]" />
        </span>
      )}
    </button>
  );
}
