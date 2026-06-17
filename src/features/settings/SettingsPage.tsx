import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { formatDistanceToNow } from "date-fns";
import { es as dateLocaleEs, enUS as dateLocaleEn } from "date-fns/locale";
import { ChevronRight, Download, Info, KeyRound, LogOut, Monitor, Palette, Smartphone, Tags } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { InstallButton } from "../../components/InstallButton";
import { Field, inputClass } from "../../components/Field";
import { PageHeader } from "../../components/PageHeader";
import { ThemeToggle } from "../../components/ThemeToggle";
import { listWalletCategories } from "../../lib/api";
import {
  changePassword,
  listSessions,
  logout,
  me,
  revokeOtherSessions,
  revokeSession,
} from "../../lib/auth";
import { deviceLabel, isMobileDevice } from "../../lib/device";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";
import { getLocale, setLocale, useLocale, type Locale } from "../../i18n/store";

/** SQLite UTC timestamp ("YYYY-MM-DD HH:MM:SS") → relative phrase in the active locale. */
function relativeFromUtc(ts: string | null): string {
  if (!ts) return "";
  const date = new Date(ts.replace(" ", "T") + "Z");
  if (Number.isNaN(date.getTime())) return ts;
  const locale = getLocale() === "en" ? dateLocaleEn : dateLocaleEs;
  return formatDistanceToNow(date, { addSuffix: true, locale });
}

function ChangePasswordForm() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: async () => {
      if (next !== confirm) throw new Error(es.account.passwordMismatch);
      await changePassword(current, next);
    },
    onSuccess: () => {
      setDone(true);
      setError(null);
      setCurrent("");
      setNext("");
      setConfirm("");
    },
    onError: (e) => {
      setDone(false);
      setError(e instanceof Error ? e.message : String(e));
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    mutation.mutate();
  };

  return (
    <form onSubmit={submit} className="grid max-w-sm gap-3">
      <Field label={es.account.currentPassword}>
        <input
          type="password"
          className={inputClass}
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          autoComplete="current-password"
          required
        />
      </Field>
      <Field label={es.account.newPassword}>
        <input
          type="password"
          className={inputClass}
          value={next}
          onChange={(e) => setNext(e.target.value)}
          autoComplete="new-password"
          minLength={8}
          required
        />
      </Field>
      <Field label={es.account.confirmPassword}>
        <input
          type="password"
          className={inputClass}
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          minLength={8}
          required
        />
      </Field>
      <p className="text-xs text-fg-subtle">{es.account.passwordChangeNote}</p>
      {error && <p className="text-sm text-danger">{error}</p>}
      {done && <p className="text-sm text-accent">{es.account.passwordChanged}</p>}
      <div>
        <Button type="submit" disabled={mutation.isPending}>
          {es.account.changePassword}
        </Button>
      </div>
    </form>
  );
}

function DevicesList() {
  const queryClient = useQueryClient();
  const sessions = useQuery({ queryKey: ["sessions"], queryFn: listSessions });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["sessions"] });
  const revokeOne = useMutation({ mutationFn: revokeSession, onSuccess: refresh });
  const revokeOthers = useMutation({
    mutationFn: revokeOtherSessions,
    onSuccess: refresh,
  });

  const list = sessions.data ?? [];
  return (
    <>
      {sessions.isPending && <p className="text-sm text-fg-subtle">{es.common.loading}</p>}
      {sessions.isError && (
        <p className="text-sm text-danger">{String(sessions.error)}</p>
      )}
      <ul className="divide-y divide-border-muted">
        {list.map((s) => {
          const Icon = isMobileDevice(s.userAgent) ? Smartphone : Monitor;
          return (
            <li key={s.id} className="flex items-center gap-3 py-2.5 text-sm">
              <Icon size={18} className="shrink-0 text-fg-subtle" />
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2 text-fg">
                  <span className="truncate">{deviceLabel(s.userAgent)}</span>
                  {s.current && (
                    <span className="rounded-full bg-accent-dim/15 px-2 py-0.5 text-[10px] font-medium text-accent">
                      {es.account.thisDevice}
                    </span>
                  )}
                </p>
                <p className="text-xs text-fg-subtle">
                  {es.account.lastSeen}: {relativeFromUtc(s.lastSeenAt ?? s.createdAt)}
                </p>
              </div>
              {!s.current && (
                <Button
                  variant="danger"
                  className="px-3 py-1.5"
                  disabled={revokeOne.isPending}
                  onClick={() => revokeOne.mutate(s.id)}
                >
                  {es.account.revoke}
                </Button>
              )}
            </li>
          );
        })}
      </ul>
      {list.filter((s) => !s.current).length > 0 && (
        <div className="mt-2">
          <Button
            variant="ghost"
            disabled={revokeOthers.isPending}
            onClick={() => revokeOthers.mutate()}
          >
            {es.account.revokeOthers}
          </Button>
        </div>
      )}
    </>
  );
}

export function SettingsPage() {
  const queryClient = useQueryClient();
  const categories = useQuery({
    queryKey: ["walletCategories"],
    queryFn: listWalletCategories,
  });
  const session = useQuery({ queryKey: ["me"], queryFn: me, staleTime: Infinity });
  const locale = useLocale();

  const doLogout = async () => {
    await logout().catch(() => {});
    queryClient.setQueryData(["me"], null);
    queryClient.clear();
  };

  return (
    <div className="mx-auto w-full max-w-5xl">
      <PageHeader title={es.settings.title} />
      <div className="gap-6 lg:columns-2 [&>section]:mb-6 [&>section]:break-inside-avoid">
        <section className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="font-medium">{es.theme.label}</h3>
          <ThemeToggle />
        </section>

        <section className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="font-medium">{es.settings.language}</h3>
          <div className="flex rounded-lg bg-surface p-1">
            {(
              [
                { code: "es", label: "Español" },
                { code: "en", label: "English" },
              ] as { code: Locale; label: string }[]
            ).map((l) => (
              <button
                key={l.code}
                type="button"
                onClick={() => setLocale(l.code)}
                className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
                  locale === l.code
                    ? "bg-surface-overlay font-medium text-fg"
                    : "text-fg-subtle hover:text-fg"
                }`}
              >
                {l.label}
              </button>
            ))}
          </div>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-1 flex items-center gap-2 font-medium">
            <Palette size={16} className="text-fg-subtle" />
            {es.appearance.title}
          </h3>
          <p className="mb-3 text-xs text-fg-subtle">{es.appearance.settingsHint}</p>
          <Link
            to="/apariencia"
            className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
          >
            {es.appearance.manage}
            <ChevronRight size={15} />
          </Link>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-1 flex items-center gap-2 font-medium">
            <Download size={16} className="text-fg-subtle" />
            {es.install.title}
          </h3>
          <p className="mb-3 text-xs text-fg-subtle">{es.install.hint}</p>
          <InstallButton showInstalled />
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-1 flex items-center gap-2 font-medium">
            <Tags size={16} className="text-fg-subtle" />
            {es.categories.title}
          </h3>
          <p className="mb-3 text-xs text-fg-subtle">{es.categories.settingsHint}</p>
          <Link
            to="/categorias"
            className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
          >
            {es.categories.manage}
            <ChevronRight size={15} />
          </Link>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 font-medium">{es.settings.walletCategories}</h3>
          <ul className="flex flex-wrap gap-2">
            {categories.data?.map((c) => (
              <li
                key={c.id}
                className="rounded-full bg-surface-overlay px-3 py-1 text-sm text-fg"
              >
                {seedName(c.name, c.isSystem)}
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 flex items-center gap-2 font-medium">
            <Smartphone size={16} className="text-fg-subtle" />
            {es.account.devices}
          </h3>
          <DevicesList />
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 flex items-center gap-2 font-medium">
            <KeyRound size={16} className="text-fg-subtle" />
            {es.account.changePassword}
          </h3>
          <ChangePasswordForm />
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 font-medium">{es.settings.session}</h3>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-fg">{session.data?.email}</span>
            <Button variant="ghost" onClick={doLogout}>
              <span className="flex items-center gap-2">
                <LogOut size={16} /> {es.auth.logout}
              </span>
            </Button>
          </div>
        </section>

        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <h3 className="mb-3 flex items-center gap-2 font-medium">
            <Info size={16} className="text-fg-subtle" />
            {es.settings.about}
          </h3>
          <div className="flex items-center justify-between text-sm">
            <span className="text-fg-muted">{es.settings.version}</span>
            <span className="font-mono text-fg">v{__APP_VERSION__}</span>
          </div>
        </section>
      </div>
    </div>
  );
}
