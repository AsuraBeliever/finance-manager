import { Sparkles } from "lucide-react";
import { useState } from "react";
import { Modal } from "../../components/Modal";
import {
  changelogEnabled,
  localizedChanges,
  markChangelogSeen,
  unseenEntries,
  type ChangelogEntry,
} from "../../lib/changelog";
import { es } from "../../i18n/es";

/** The changelog modal body: each release with its bullet list. */
export function WhatsNewModal({
  open,
  onClose,
  entries,
}: {
  open: boolean;
  onClose: () => void;
  entries: ChangelogEntry[];
}) {
  return (
    <Modal open={open} onClose={onClose} title={es.whatsNew.title}>
      {entries.length === 0 ? (
        <p className="text-sm text-fg-subtle">{es.whatsNew.empty}</p>
      ) : (
        <div className="space-y-5">
          {entries.map((e) => (
            <div key={e.version}>
              <div className="mb-2 flex items-baseline gap-2">
                <span className="font-display text-base font-semibold text-fg">
                  v{e.version}
                </span>
                <span className="text-xs text-fg-subtle">{e.date}</span>
              </div>
              <ul className="space-y-1.5">
                {localizedChanges(e).map((c, i) => (
                  <li key={i} className="flex gap-2 text-sm text-fg-muted">
                    <Sparkles size={14} className="mt-0.5 shrink-0 text-accent" />
                    <span>{c}</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}

/** Mounted once in the app shell: shows the changelog automatically when the
 *  running build is newer than what the user last saw, unless they turned it
 *  off. Closing marks the current version as seen so it won't show again. */
export function WhatsNewAuto() {
  const current = __APP_VERSION__;
  const [entries] = useState<ChangelogEntry[]>(() =>
    changelogEnabled() ? unseenEntries(current) : [],
  );
  const [open, setOpen] = useState(entries.length > 0);

  const close = () => {
    markChangelogSeen(current);
    setOpen(false);
  };

  if (entries.length === 0) return null;
  return <WhatsNewModal open={open} onClose={close} entries={entries} />;
}
