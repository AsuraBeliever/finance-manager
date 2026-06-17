import { Banknote, Coins, PiggyBank, Wallet as WalletIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { formatCents } from "../lib/money";
import { effectiveSkin, isImageSkin, resolveSkin, skinArt } from "../lib/skins";
import type { Wallet } from "../lib/types";
import { es } from "../i18n/es";
import { seedName } from "../i18n/seed";

const ART_ICON = { banknote: Banknote, wallet: WalletIcon, coins: Coins, piggy: PiggyBank } as const;

/** A wallet rendered as a crystalline card. The skin (explicit, or the default
 *  for its category) sets the look: card skins show a chip; cash/savings skins
 *  show a money illustration instead. */
export function WalletCard({ wallet }: { wallet: Wallet }) {
  // No explicit skin → use the category's default so each category is distinct.
  const skin = effectiveSkin(wallet.skin, wallet.categoryName);
  const { background, fg } = resolveSkin(skin, wallet.color);
  const img = isImageSkin(skin);
  const art = skinArt(skin);
  const Motif = ART_ICON[art as keyof typeof ART_ICON];

  return (
    <Link
      to={`/carteras/${wallet.id}`}
      className="group relative block aspect-[1.586/1] w-full overflow-hidden rounded-2xl shadow-card transition-transform duration-300 hover:-translate-y-1"
      style={{ background, color: fg }}
    >
      {/* crystalline gloss + soft top-left highlight */}
      <span
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "linear-gradient(120deg, rgba(255,255,255,0.30) 0%, rgba(255,255,255,0.06) 22%, transparent 46%)",
        }}
      />
      <span
        className="pointer-events-none absolute -top-1/3 -left-1/4 h-2/3 w-2/3 rounded-full opacity-70"
        style={{ background: "radial-gradient(closest-side, rgba(255,255,255,0.28), transparent)" }}
      />
      {/* big faint money motif watermark for cash skins */}
      {Motif && (
        <Motif
          className="pointer-events-none absolute -right-2 top-1/2 -translate-y-1/2 opacity-[0.14]"
          size={150}
          strokeWidth={1.25}
        />
      )}
      {/* legibility scrim for imported photos */}
      {img && (
        <span
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "linear-gradient(180deg, rgba(0,0,0,0.18) 0%, transparent 38%, rgba(0,0,0,0.55) 100%)",
          }}
        />
      )}
      <span className="pointer-events-none absolute inset-0 rounded-2xl ring-1 ring-inset ring-white/15" />

      <div className="relative flex h-full flex-col justify-between p-5">
        <div className="flex items-start justify-between gap-2">
          <span className="truncate text-base font-semibold [text-shadow:0_1px_3px_rgba(0,0,0,0.25)]">
            {wallet.name}
          </span>
          {wallet.isArchived ? (
            <span className="shrink-0 rounded-full bg-black/30 px-2 py-0.5 text-[10px] font-medium">
              {es.wallets.archived}
            </span>
          ) : (
            <span className="shrink-0 text-xs font-medium opacity-80">{wallet.currencyCode}</span>
          )}
        </div>

        {/* motif slot: chip for cards, a small money icon for cash, else spacer */}
        {art === "chip" ? (
          <div className="h-7 w-10 rounded-[5px] bg-gradient-to-br from-amber-100/95 to-amber-400/85 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.12)]">
            <div className="mx-auto mt-1 h-0.5 w-6 rounded-full bg-black/15" />
            <div className="mx-auto mt-1 h-0.5 w-6 rounded-full bg-black/15" />
            <div className="mx-auto mt-1 h-0.5 w-6 rounded-full bg-black/15" />
          </div>
        ) : Motif ? (
          <Motif
            size={30}
            strokeWidth={2}
            className="[filter:drop-shadow(0_1px_2px_rgba(0,0,0,0.35))]"
          />
        ) : (
          <div className="h-7" />
        )}

        <div>
          <p className="font-display text-2xl font-semibold tabular-nums [text-shadow:0_1px_4px_rgba(0,0,0,0.3)]">
            {formatCents(wallet.balanceCents, wallet.currencyCode)}
          </p>
          <p className="mt-0.5 text-xs opacity-80">{seedName(wallet.categoryName)}</p>
        </div>
      </div>
    </Link>
  );
}
