import { useEffect, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { TrendingUp } from "lucide-react";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Field";
import { GoogleIcon } from "../../components/GoogleIcon";
import { es } from "../../i18n/es";
import { login, register } from "../../lib/auth";

/** Login / registration screen, shown instead of the app layout when there is
 *  no session. On success the "me" query is refreshed and App re-renders. */
export function LoginPage() {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // The Google callback redirects here with ?authError=google on failure.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("authError") === "google") {
      setError(es.auth.googleError);
      window.history.replaceState(null, "", window.location.pathname + window.location.hash);
    }
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const user =
        mode === "login"
          ? await login(email, password)
          : await register(email, password, inviteCode);
      queryClient.setQueryData(["me"], user);
      queryClient.invalidateQueries();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-dim/20 text-accent">
            <TrendingUp size={22} />
          </span>
          <h1 className="text-2xl font-semibold tracking-tight">{es.app.name}</h1>
        </div>
        <form
          onSubmit={submit}
          className="flex flex-col gap-4 rounded-2xl border border-border-muted bg-surface-raised p-6"
        >
          <h2 className="text-lg font-medium">
            {mode === "login" ? es.auth.loginTitle : es.auth.registerTitle}
          </h2>

          {/* OAuth needs a full-page navigation, not a fetch. */}
          <button
            type="button"
            onClick={() => {
              window.location.href = "/api/auth/google/start";
            }}
            className="flex items-center justify-center gap-3 rounded-lg border border-border-muted bg-white px-4 py-2 text-sm font-medium text-zinc-800 transition-colors hover:bg-zinc-100"
          >
            <GoogleIcon size={18} />
            {es.auth.continueWithGoogle}
          </button>
          <div className="flex items-center gap-3 text-xs text-zinc-600">
            <span className="h-px flex-1 bg-border-muted" />
            {es.auth.or}
            <span className="h-px flex-1 bg-border-muted" />
          </div>
          <Field label={es.auth.email}>
            <input
              type="email"
              className={inputClass}
              placeholder={es.auth.emailPlaceholder}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </Field>
          <Field label={es.auth.password}>
            <input
              type="password"
              className={inputClass}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={mode === "register" ? 8 : undefined}
              required
            />
            {mode === "register" && (
              <p className="mt-1 text-xs text-zinc-500">{es.auth.passwordHint}</p>
            )}
          </Field>
          {mode === "register" && (
            <Field label={es.auth.inviteCode}>
              <input
                type="text"
                className={inputClass}
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                autoComplete="off"
                required
              />
              <p className="mt-1 text-xs text-zinc-500">{es.auth.inviteCodeHint}</p>
            </Field>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button type="submit" disabled={busy}>
            {mode === "login" ? es.auth.login : es.auth.register}
          </Button>
          <button
            type="button"
            className="text-sm text-zinc-400 transition-colors hover:text-accent"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login");
              setError(null);
            }}
          >
            {mode === "login" ? es.auth.switchToRegister : es.auth.switchToLogin}
          </button>
        </form>
      </div>
    </div>
  );
}
