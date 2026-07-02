import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Check, Palette, Upload } from "lucide-react";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import {
  convertGoalToWallet,
  createWallet,
  listCurrencies,
  listWalletCategories,
  listWallets,
  updateWallet,
  type WalletInput,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import { effectiveSkin, resolveSkin, skinAccent, SKINS, type SkinGroup } from "../../lib/skins";
import type { Wallet } from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";
import { getLocale } from "../../i18n/store";

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

/** Graduating a fund goal into a wallet: the form is prefilled with the goal's
 *  style and, on save, runs the conversion instead of a plain create. Balance
 *  comes from the transfer (the field is hidden). */
export interface WalletConvert {
  goalId: number;
  name: string;
  color: string | null;
  currencyCode: string;
  savedCents: number;
  sourceCategoryId: number | null;
  /** Wallet the goal was saved in — the graduated fund nests under it. */
  sourceWalletId: number | null;
}

interface WalletFormModalProps {
  open: boolean;
  onClose: () => void;
  /** When set, the form edits this wallet instead of creating one. */
  wallet?: Wallet;
  /** When set, the form graduates a fund goal into a new wallet. */
  convert?: WalletConvert;
  /** When set, a new wallet is created as an apartado of this parent (category
   *  and currency inherited from it). Used by "add pocket" on a wallet. */
  defaultParent?: { id: number; categoryId: number; currencyCode: string };
}

export function WalletFormModal({
  open,
  onClose,
  wallet,
  convert,
  defaultParent,
}: WalletFormModalProps) {
  const queryClient = useQueryClient();
  const categories = useQuery({
    queryKey: ["walletCategories"],
    queryFn: listWalletCategories,
  });
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const walletsQ = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [currencyCode, setCurrencyCode] = useState("MXN");
  const [balanceText, setBalanceText] = useState("");
  const [parentWalletId, setParentWalletId] = useState<number | null>(null);
  const [skin, setSkin] = useState<string | null>(null);
  const [customColor, setCustomColor] = useState(COLORS[0]);
  const [notes, setNotes] = useState("");
  const [yieldEnabled, setYieldEnabled] = useState(false);
  const [yieldRateText, setYieldRateText] = useState("");
  const [yieldFrequency, setYieldFrequency] = useState("weekly");
  const [creditEnabled, setCreditEnabled] = useState(false);
  const [cutDayText, setCutDayText] = useState("");
  const [dueDaysText, setDueDaysText] = useState("");
  const [creditLimitText, setCreditLimitText] = useState("");
  const [annivMonth, setAnnivMonth] = useState(""); // "" = untracked, else "1".."12"
  const [annivDayText, setAnnivDayText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    if (convert) {
      setName(convert.name);
      setCategoryId(convert.sourceCategoryId);
      setCurrencyCode(convert.currencyCode);
      setBalanceText(""); // balance arrives via the transfer, not editable here
      setParentWalletId(convert.sourceWalletId); // nest under the source wallet
      setSkin(null);
      setCustomColor(convert.color ?? COLORS[0]);
      setNotes("");
      setYieldEnabled(false);
      setCreditEnabled(false);
      setError(null);
      return;
    }
    if (!wallet && defaultParent) {
      // New apartado: inherit the parent's category + currency (hidden fields).
      setName("");
      setCategoryId(defaultParent.categoryId);
      setCurrencyCode(defaultParent.currencyCode);
      setBalanceText("");
      setParentWalletId(defaultParent.id);
      setSkin(null);
      setCustomColor(COLORS[0]);
      setNotes("");
      setYieldEnabled(false);
      setCreditEnabled(false);
      setError(null);
      return;
    }
    setName(wallet?.name ?? "");
    setCategoryId(wallet?.categoryId ?? null);
    setCurrencyCode(wallet?.currencyCode ?? "MXN");
    setBalanceText(wallet ? (wallet.initialBalanceCents / 100).toFixed(2) : "");
    setParentWalletId(wallet?.parentWalletId ?? null);
    setSkin(wallet?.skin ?? null);
    setCustomColor(skinAccent(wallet?.skin) ?? wallet?.color ?? COLORS[0]);
    setNotes(wallet?.notes ?? "");
    setYieldEnabled(wallet?.yieldRateBps != null);
    setYieldRateText(wallet?.yieldRateBps != null ? String(wallet.yieldRateBps / 100) : "");
    setYieldFrequency(wallet?.yieldFrequency ?? "weekly");
    setCreditEnabled(wallet?.creditCutDay != null);
    setCutDayText(wallet?.creditCutDay != null ? String(wallet.creditCutDay) : "");
    setDueDaysText(wallet?.creditDueDays != null ? String(wallet.creditDueDays) : "");
    setCreditLimitText(
      wallet?.creditLimitCents != null ? (wallet.creditLimitCents / 100).toFixed(2) : "",
    );
    const [am, ad] = (wallet?.creditAnniversary ?? "").split("-");
    setAnnivMonth(am ? String(Number(am)) : "");
    setAnnivDayText(ad ? String(Number(ad)) : "");
    setError(null);
  }, [open, wallet, convert, defaultParent]);

  const invalidateAfterConvert = () => {
    for (const key of ["wallets", "savingsGoals", "transactions", "dashboard"]) {
      queryClient.invalidateQueries({ queryKey: [key] });
    }
    onClose();
  };

  const mutation = useMutation({
    mutationFn: (input: WalletInput) =>
      wallet ? updateWallet(wallet.id, input) : createWallet(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      onClose();
    },
    onError: (e) => setError(String(e)),
  });

  const convertMut = useMutation({
    mutationFn: (s: {
      name: string;
      color: string;
      categoryId: number;
      skin: string | null;
      notes: string | null;
      parentWalletId: number | null;
    }) => convertGoalToWallet(convert!.goalId, s),
    onSuccess: invalidateAfterConvert,
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
    const finalCategory = categoryId ?? categories.data?.[0]?.id;
    if (finalCategory === undefined) return;
    const catNameFor = categories.data?.find((c) => c.id === finalCategory)?.name;

    if (convert) {
      convertMut.mutate({
        name,
        color: skinAccent(effectiveSkin(skin, catNameFor)) ?? COLORS[0],
        categoryId: finalCategory,
        skin,
        notes: notes.trim() === "" ? null : notes.trim(),
        parentWalletId,
      });
      return;
    }

    const cents = balanceText.trim() === "" ? 0 : parseToCents(balanceText);
    if (cents === null) {
      setError(es.wallets.invalidAmount);
      return;
    }
    let yieldRateBps: number | null = null;
    if (yieldEnabled) {
      const pct = parseFloat(yieldRateText.replace(",", "."));
      if (!isFinite(pct) || pct <= 0) {
        setError(es.wallets.invalidAmount);
        return;
      }
      yieldRateBps = Math.round(pct * 100);
    }
    let creditCutDay: number | null = null;
    let creditDueDays: number | null = null;
    let creditLimitCents: number | null = null;
    let creditAnniversary: string | null = null;
    if (creditEnabled) {
      creditCutDay = parseInt(cutDayText, 10);
      if (!isFinite(creditCutDay) || creditCutDay < 1 || creditCutDay > 31) {
        setError(es.credit.invalidCutDay);
        return;
      }
      if (dueDaysText.trim() !== "") {
        creditDueDays = parseInt(dueDaysText, 10);
        if (!isFinite(creditDueDays) || creditDueDays < 1 || creditDueDays > 60) {
          setError(es.credit.invalidDueDays);
          return;
        }
      }
      if (creditLimitText.trim() !== "") {
        creditLimitCents = parseToCents(creditLimitText);
        if (creditLimitCents === null || creditLimitCents <= 0) {
          setError(es.wallets.invalidAmount);
          return;
        }
      }
      const annivDay = parseInt(annivDayText, 10);
      if (annivMonth !== "" && isFinite(annivDay) && annivDay >= 1 && annivDay <= 31) {
        creditAnniversary = `${String(annivMonth).padStart(2, "0")}-${String(annivDay).padStart(2, "0")}`;
      }
    }

    const catName = categories.data?.find((c) => c.id === finalCategory)?.name;
    mutation.mutate({
      name,
      categoryId: finalCategory,
      currencyCode,
      initialBalanceCents: cents,
      color: skinAccent(effectiveSkin(skin, catName)) ?? COLORS[0],
      skin,
      notes: notes.trim() === "" ? null : notes.trim(),
      parentWalletId,
      yieldRateBps,
      yieldFrequency: yieldRateBps != null ? yieldFrequency : null,
      creditCutDay,
      creditDueDays,
      creditLimitCents,
      creditAnniversary,
    });
  }

  // Only top-level, non-archived wallets can be a parent (apartados stay one
  // level deep), and never the wallet being edited itself.
  const eligibleParents = (walletsQ.data ?? []).filter(
    (w) => w.id !== wallet?.id && w.parentWalletId == null && !w.isArchived,
  );

  const monthLocale = getLocale() === "en" ? "en-US" : "es-MX";
  const monthNames = Array.from({ length: 12 }, (_, i) =>
    new Date(2000, i, 1).toLocaleDateString(monthLocale, { month: "long" }),
  );

  const imgSelected = !!skin && skin.startsWith("img:");
  const gradSelected = !!skin && skin.startsWith("grad:");
  // "Auto" previews the default skin for the currently selected category.
  const selectedCatName = categories.data?.find(
    (c) => c.id === (categoryId ?? categories.data?.[0]?.id),
  )?.name;
  const autoBg = resolveSkin(effectiveSkin(null, selectedCatName)).background;

  return (
    <Modal
      title={convert ? es.goals.convertToWallet : wallet ? es.wallets.editWallet : es.wallets.newWallet}
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

        <Field label={es.wallets.parentWallet}>
          <select
            className={inputClass}
            value={parentWalletId ?? ""}
            onChange={(e) => {
              const pid = e.target.value === "" ? null : Number(e.target.value);
              setParentWalletId(pid);
              // An apartado inherits its parent's category + currency.
              const par = walletsQ.data?.find((w) => w.id === pid);
              if (par) {
                setCategoryId(par.categoryId);
                setCurrencyCode(par.currencyCode);
              }
            }}
          >
            <option value="">{es.wallets.parentNone}</option>
            {eligibleParents.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-fg-subtle">
            {parentWalletId != null ? es.wallets.parentHint : es.wallets.parentNoneHint}
          </span>
        </Field>

        {/* An apartado inherits its parent's category and currency, so those
            aren't shown — only a standalone wallet picks them. */}
        {parentWalletId == null && (
          <div className="grid grid-cols-2 gap-3">
            <Field label={es.wallets.category}>
              <select
                className={inputClass}
                value={categoryId ?? categories.data?.[0]?.id ?? ""}
                onChange={(e) => {
                  const id = Number(e.target.value);
                  setCategoryId(id);
                  // Picking the credit-card category on a new wallet suggests
                  // credit mode; it stays a manual toggle otherwise.
                  const cat = categories.data?.find((c) => c.id === id);
                  if (!wallet && cat?.name === "Tarjeta de crédito") setCreditEnabled(true);
                }}
              >
                {categories.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {seedName(c.name, c.isSystem)}
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
                    {c.code} — {seedName(c.name)}
                  </option>
                ))}
              </select>
            </Field>
          </div>
        )}

        {convert ? (
          <p className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
            {es.goals.convertMoves.replace(
              "{amount}",
              formatCents(convert.savedCents, convert.currencyCode),
            )}
          </p>
        ) : (
          <Field label={es.wallets.initialBalance}>
            <MoneyInput value={balanceText} onChange={setBalanceText} />
          </Field>
        )}

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
              {/* Custom color: opens the OS color palette and builds a gradient */}
              <label
                title={es.wallets.skinCustom}
                className={`relative h-11 w-[4.5rem] cursor-pointer overflow-hidden rounded-lg ring-1 ring-inset ring-white/15 transition-transform hover:scale-105 ${
                  gradSelected ? "scale-105 outline outline-2 outline-accent" : ""
                }`}
                style={{ background: resolveSkin(`grad:${customColor}`).background }}
              >
                <span
                  className="pointer-events-none absolute inset-0"
                  style={{ background: "linear-gradient(120deg, rgba(255,255,255,0.35) 0%, transparent 45%)" }}
                />
                <span className="absolute inset-0 flex items-center justify-center">
                  {gradSelected ? (
                    <Check size={16} className="text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.6)]" />
                  ) : (
                    <Palette size={15} className="text-white/90 drop-shadow-[0_1px_2px_rgba(0,0,0,0.6)]" />
                  )}
                </span>
                <input
                  type="color"
                  value={customColor}
                  onChange={(e) => {
                    setCustomColor(e.target.value);
                    setSkin(`grad:${e.target.value}`);
                  }}
                  className="absolute inset-0 cursor-pointer opacity-0"
                />
              </label>
              {imgSelected && (
                <SkinSwatch background={resolveSkin(skin).background} selected onClick={() => {}} />
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

        <Field label={es.wallets.notes}>
          <input
            className={inputClass}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Field>

        {/* Yield: wallets that grow on their own (Klar, Nu…) accrue interest
            automatically via the daily cron; see worker wallet_yield. Not shown
            when graduating a fund goal (set it later by editing the wallet). */}
        {!convert && (
        <div className="rounded-lg border border-border-muted p-3">
          <label className="flex cursor-pointer items-start gap-2.5">
            <input
              type="checkbox"
              checked={yieldEnabled}
              onChange={(e) => setYieldEnabled(e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-accent"
            />
            <span className="text-sm">
              {es.wallets.yieldEnable}
              <span className="mt-0.5 block text-xs text-fg-subtle">
                {es.wallets.yieldHint}
              </span>
            </span>
          </label>

          {yieldEnabled && (
            <div className="mt-3 space-y-2">
              <div className="grid grid-cols-2 gap-3">
                <Field label={es.wallets.yieldRate}>
                  <div className="relative">
                    <input
                      className={inputClass}
                      value={yieldRateText}
                      onChange={(e) => setYieldRateText(e.target.value)}
                      placeholder="3.0"
                      inputMode="decimal"
                    />
                    <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-fg-subtle">
                      %
                    </span>
                  </div>
                </Field>
                <Field label={es.wallets.yieldFrequency}>
                  <select
                    className={inputClass}
                    value={yieldFrequency}
                    onChange={(e) => setYieldFrequency(e.target.value)}
                  >
                    <option value="weekly">{es.wallets.yieldWeekly}</option>
                    <option value="biweekly">{es.wallets.yieldBiweekly}</option>
                    <option value="monthly">{es.wallets.yieldMonthly}</option>
                  </select>
                </Field>
              </div>
              {!wallet?.yieldRateBps && (
                <p className="text-xs text-fg-subtle">{es.wallets.yieldStartsToday}</p>
              )}
            </div>
          )}
        </div>
        )}

        {/* Credit card: cut day is the switch; everything else is optional.
            Spending drives the balance negative (= debt) and the detail page
            gains the statement panel. Not shown when graduating a goal. */}
        {!convert && (
        <div className="rounded-lg border border-border-muted p-3">
          <label className="flex cursor-pointer items-start gap-2.5">
            <input
              type="checkbox"
              checked={creditEnabled}
              onChange={(e) => setCreditEnabled(e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-accent"
            />
            <span className="text-sm">
              {es.credit.enable}
              <span className="mt-0.5 block text-xs text-fg-subtle">
                {es.credit.enableHint}
              </span>
            </span>
          </label>

          {creditEnabled && (
            <div className="mt-3 space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <Field label={es.credit.cutDay}>
                  <input
                    className={inputClass}
                    value={cutDayText}
                    onChange={(e) => setCutDayText(e.target.value)}
                    placeholder="15"
                    inputMode="numeric"
                  />
                  <span className="mt-1 block text-xs text-fg-subtle">
                    {es.credit.cutDayHint}
                  </span>
                </Field>
                <Field label={es.credit.dueDays}>
                  <input
                    className={inputClass}
                    value={dueDaysText}
                    onChange={(e) => setDueDaysText(e.target.value)}
                    placeholder="20"
                    inputMode="numeric"
                  />
                  <span className="mt-1 block text-xs text-fg-subtle">
                    {es.credit.dueDaysHint}
                  </span>
                </Field>
              </div>
              <Field label={es.credit.limit}>
                <MoneyInput value={creditLimitText} onChange={setCreditLimitText} />
                <span className="mt-1 block text-xs text-fg-subtle">
                  {es.credit.limitHint}
                </span>
              </Field>
              <Field label={es.credit.anniversary}>
                <div className="grid grid-cols-2 gap-3">
                  <select
                    className={inputClass}
                    value={annivMonth}
                    onChange={(e) => setAnnivMonth(e.target.value)}
                  >
                    <option value="">{es.credit.anniversaryNone}</option>
                    {monthNames.map((m, i) => (
                      <option key={m} value={i + 1}>
                        {m}
                      </option>
                    ))}
                  </select>
                  <input
                    className={inputClass}
                    value={annivDayText}
                    onChange={(e) => setAnnivDayText(e.target.value)}
                    placeholder={es.credit.anniversaryDay}
                    inputMode="numeric"
                    disabled={annivMonth === ""}
                  />
                </div>
              </Field>
            </div>
          )}
        </div>
        )}

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
          <Button type="submit" disabled={convert ? convertMut.isPending : mutation.isPending}>
            {convert ? es.goals.convertToWallet : es.common.save}
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
