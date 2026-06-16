import { RotateCcw, Upload, X } from "lucide-react";
import { useRef } from "react";
import { Button } from "../../components/Button";
import { ColorPicker } from "../../components/ColorPicker";
import { Field, inputClass } from "../../components/Field";
import { PageHeader } from "../../components/PageHeader";
import {
  FONTS,
  ICONS,
  resetAppearance,
  setAppearance,
  useAppearance,
  type FontKey,
  type IconKey,
} from "../../lib/appearance";
import { es } from "../../i18n/es";

const DEFAULT_ACCENT = "#a855f7";
const DEFAULT_GOLD = "#22d3ee";

/** Read an image file and downscale it to a small square-ish PNG data URL. */
function fileToLogo(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("image"));
      img.onload = () => {
        const max = 128;
        const scale = Math.min(1, max / Math.max(img.width, img.height));
        const w = Math.max(1, Math.round(img.width * scale));
        const h = Math.max(1, Math.round(img.height * scale));
        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext("2d");
        if (!ctx) return reject(new Error("ctx"));
        ctx.drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL("image/png"));
      };
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  });
}

export function AppearancePage() {
  const a = useAppearance();
  const fileRef = useRef<HTMLInputElement>(null);
  const BrandIcon = ICONS[a.icon] ?? ICONS["trending-up"];

  const onLogoFile = async (file: File | undefined) => {
    if (!file) return;
    try {
      setAppearance({ logo: await fileToLogo(file) });
    } catch {
      /* ignore unreadable image */
    }
  };

  return (
    <div className="mx-auto w-full max-w-2xl">
      <PageHeader
        title={es.appearance.title}
        actions={
          <Button variant="ghost" onClick={() => resetAppearance()}>
            <span className="flex items-center gap-2">
              <RotateCcw size={15} /> {es.appearance.reset}
            </span>
          </Button>
        }
      />
      <p className="mb-5 -mt-3 text-sm text-fg-subtle">{es.appearance.settingsHint}</p>

      <div className="flex flex-col gap-6">
        {/* Colors */}
        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-4 font-medium">{es.appearance.colors}</h3>
          {/* Live preview: accent → secondary, like the net-worth headline. */}
          <div className="mb-5 flex flex-wrap items-center gap-4 rounded-lg bg-surface p-4">
            <span className="text-gold-gradient font-display text-3xl font-semibold tabular-nums">
              $12,345.00
            </span>
            <span className="inline-flex items-center gap-1.5 text-sm text-fg-muted">
              <span className="h-3 w-3 rounded-full bg-accent" /> {es.appearance.accent}
              <span className="ml-3 h-3 w-3 rounded-full bg-gold" /> {es.appearance.secondary}
            </span>
          </div>
          <div className="flex flex-col gap-4">
            <Field label={es.appearance.accent}>
              <ColorPicker
                value={a.accent ?? DEFAULT_ACCENT}
                onChange={(c) => setAppearance({ accent: c })}
              />
            </Field>
            <Field label={es.appearance.secondary}>
              <ColorPicker
                value={a.gold ?? DEFAULT_GOLD}
                onChange={(c) => setAppearance({ gold: c })}
              />
            </Field>
          </div>
        </section>

        {/* Typography */}
        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 font-medium">{es.appearance.font}</h3>
          <div className="flex flex-wrap gap-2">
            {(Object.keys(FONTS) as FontKey[]).map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => setAppearance({ font: key })}
                style={{ fontFamily: FONTS[key].display ?? undefined }}
                className={`rounded-lg border px-3 py-2 text-sm transition-colors ${
                  a.font === key
                    ? "border-accent bg-accent-dim/15 text-accent"
                    : "border-border-muted text-fg hover:bg-surface-overlay"
                }`}
              >
                {es.appearance.fonts[key]}
              </button>
            ))}
          </div>
        </section>

        {/* Brand & logo */}
        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-4 font-medium">{es.appearance.brand}</h3>

          {/* live preview */}
          <div className="mb-4 flex items-center gap-2.5 rounded-lg bg-surface p-3">
            {a.logo ? (
              <img src={a.logo} alt="" className="h-9 w-9 shrink-0 rounded-xl object-cover" />
            ) : (
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent-dim text-white">
                <BrandIcon size={18} />
              </span>
            )}
            <span className="truncate font-display text-xl font-semibold tracking-tight text-fg">
              {a.appName || "Finanzas"}
            </span>
          </div>

          <Field label={es.appearance.appName}>
            <input
              className={inputClass}
              value={a.appName}
              maxLength={24}
              onChange={(e) => setAppearance({ appName: e.target.value })}
            />
          </Field>

          <div className="mt-4">
            <p className="mb-2 text-sm text-fg-muted">{es.appearance.icon}</p>
            <div className="flex flex-wrap gap-2">
              {(Object.keys(ICONS) as IconKey[]).map((key) => {
                const Icon = ICONS[key];
                const selected = a.icon === key && !a.logo;
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setAppearance({ icon: key, logo: "" })}
                    className={`flex h-10 w-10 items-center justify-center rounded-lg border transition-colors ${
                      selected
                        ? "border-accent bg-accent-dim/15 text-accent"
                        : "border-border-muted text-fg-muted hover:bg-surface-overlay hover:text-fg"
                    }`}
                  >
                    <Icon size={18} />
                  </button>
                );
              })}
            </div>
          </div>

          <div className="mt-4">
            <p className="mb-2 text-sm text-fg-muted">{es.appearance.logo}</p>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="ghost" className="px-3 py-2" onClick={() => fileRef.current?.click()}>
                <span className="flex items-center gap-1.5">
                  <Upload size={15} /> {es.appearance.uploadLogo}
                </span>
              </Button>
              {a.logo && (
                <Button variant="ghost" className="px-3 py-2" onClick={() => setAppearance({ logo: "" })}>
                  <span className="flex items-center gap-1.5">
                    <X size={15} /> {es.appearance.removeLogo}
                  </span>
                </Button>
              )}
              <span className="text-xs text-fg-subtle">{es.appearance.logoHint}</span>
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => onLogoFile(e.target.files?.[0])}
            />
          </div>
        </section>
      </div>
    </div>
  );
}
