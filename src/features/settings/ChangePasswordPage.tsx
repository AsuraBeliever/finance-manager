import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Field";
import { PageHeader } from "../../components/PageHeader";
import { changePassword } from "../../lib/auth";
import { es } from "../../i18n/es";

export function ChangePasswordPage() {
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
    <div className="mx-auto w-full max-w-md">
      <PageHeader title={es.account.changePassword} backTo="/ajustes" backLabel={es.settings.back} />
      <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
        <form onSubmit={submit} className="grid gap-3">
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
      </section>
    </div>
  );
}
