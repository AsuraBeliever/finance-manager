// Shared MSI schedule UI: the live "≈ $X/mo · first charge on…" line while
// typing and the save confirmation body. Used by the card panel's MSI form
// and the generic transaction form, so both feel identical. The schedule is
// always computed by the server (it owns the cut day) via preview_msi_plan.
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { previewMsiPlan } from "../../lib/api";
import { formatDayMonth } from "../../lib/date";
import { formatCents, parseToCents } from "../../lib/money";
import type { MsiSchedulePreview } from "../../lib/types";
import { es } from "../../i18n/es";

/** Debounced live schedule for the amount/months/date currently typed.
 *  Returns nothing until the inputs form a valid plan. */
export function useMsiPreview(
  walletId: number | undefined,
  totalText: string,
  monthsText: string,
  purchasedAt: string,
  enabled: boolean,
): MsiSchedulePreview | undefined {
  const [key, setKey] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled || walletId === undefined) {
      setKey(null);
      return;
    }
    const cents = parseToCents(totalText);
    const months = parseInt(monthsText, 10);
    if (cents === null || cents <= 0 || !isFinite(months) || months < 2 || months > 60) {
      setKey(null);
      return;
    }
    // Debounce keystrokes so we don't hit the server per character.
    const t = setTimeout(
      () => setKey(JSON.stringify([walletId, cents, months, purchasedAt])),
      350,
    );
    return () => clearTimeout(t);
  }, [enabled, walletId, totalText, monthsText, purchasedAt]);

  const preview = useQuery({
    queryKey: ["msiPreview", key],
    queryFn: () => {
      const [wid, cents, months, date] = JSON.parse(key!) as [number, number, number, string];
      return previewMsiPlan({ walletId: wid, totalCents: cents, months, purchasedAt: date });
    },
    enabled: key !== null,
    staleTime: 5 * 60_000,
    retry: false,
  });
  return key !== null ? preview.data : undefined;
}

/** The live schedule line(s) under the form fields. */
export function MsiPreviewLine({ preview, currency }: { preview?: MsiSchedulePreview; currency: string }) {
  if (!preview) return null;
  return (
    <div className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
      <p>
        {es.credit.msiPreviewLine
          .replace("{monthly}", formatCents(preview.monthlyCents, currency))
          .replace("{amount}", formatCents(preview.firstChargeCents, currency))
          .replace("{date}", formatDayMonth(preview.firstChargeDate))}
      </p>
      {preview.alreadyBilledMonths > 0 && (
        <p className="mt-1">
          {es.credit.msiPreviewBackdated
            .replace("{n}", String(preview.alreadyBilledMonths))
            .replace("{amount}", formatCents(preview.alreadyBilledCents, currency))}
        </p>
      )}
    </div>
  );
}

/** Confirmation body shown after saving: what will bill, when it starts and
 *  ends, and what already posted for back-dated purchases. */
export function MsiSavedInfo({ preview, currency }: { preview: MsiSchedulePreview; currency: string }) {
  return (
    <div className="rounded-lg bg-surface-overlay px-3 py-2.5 text-sm text-fg-muted">
      <p>
        {es.credit.msiSavedBody
          .replace("{amount}", formatCents(preview.firstChargeCents, currency))
          .replace("{first}", formatDayMonth(preview.firstChargeDate))
          .replace("{last}", formatDayMonth(preview.lastChargeDate))}
      </p>
      {preview.alreadyBilledMonths > 0 && (
        <p className="mt-1.5">
          {es.credit.msiSavedBackdated
            .replace("{n}", String(preview.alreadyBilledMonths))
            .replace("{amount}", formatCents(preview.alreadyBilledCents, currency))}
        </p>
      )}
    </div>
  );
}
