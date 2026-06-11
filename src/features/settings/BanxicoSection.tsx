import { openUrl } from "@tauri-apps/plugin-opener";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, ExternalLink } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { inputClass } from "../../components/Field";
import { getSetting, setSetting } from "../../lib/api";
import { es } from "../../i18n/es";

const TOKEN_URL = "https://www.banxico.org.mx/SieAPIRest/service/v1/token";

export function BanxicoSection() {
  const queryClient = useQueryClient();
  const stored = useQuery({
    queryKey: ["settings", "banxico_token"],
    queryFn: () => getSetting("banxico_token"),
  });
  const [token, setToken] = useState("");

  useEffect(() => {
    if (stored.data !== undefined) setToken(stored.data ?? "");
  }, [stored.data]);

  const save = useMutation({
    mutationFn: () => setSetting("banxico_token", token.trim()),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["settings", "banxico_token"] }),
  });

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 font-medium">{es.settings.banxico}</h3>
      <p className="mb-3 text-xs text-zinc-500">{es.settings.banxicoHint}</p>
      <div className="flex items-center gap-3">
        <input
          className={`${inputClass} flex-1 font-mono text-xs`}
          value={token}
          onChange={(e) => setToken(e.target.value)}
          placeholder={es.settings.banxicoTokenPlaceholder}
          spellCheck={false}
        />
        <Button variant="ghost" onClick={() => save.mutate()} disabled={save.isPending}>
          {save.isSuccess ? (
            <span className="flex items-center gap-1 text-accent">
              <Check size={14} /> {es.settings.banxicoSaved}
            </span>
          ) : (
            es.common.save
          )}
        </Button>
      </div>
      <button
        type="button"
        onClick={() => openUrl(TOKEN_URL)}
        className="mt-2 inline-flex items-center gap-1.5 text-xs text-accent hover:underline"
      >
        <ExternalLink size={12} /> {es.settings.banxicoGetToken}
      </button>
    </section>
  );
}
