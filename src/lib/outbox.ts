// Offline capture queue (outbox), deliberately append-only: only the three
// transaction-capture commands can be queued, so replaying never conflicts
// with edits made elsewhere. Each item carries a clientId the server uses for
// idempotency — a replay after a dropped response can't insert twice.
//
// Queue truth lives in localStorage; the server stays the source of truth for
// balances (pending items are listed separately, never merged into totals).

import { NetworkError, rpc } from "./api";

export type OutboxCommand = "add_income" | "add_expense" | "add_transfer";

export interface OutboxItem {
  /** Also sent as args.clientId for server-side idempotency. */
  id: string;
  command: OutboxCommand;
  args: Record<string, unknown>;
  createdAt: string; // ISO
  status: "pending" | "error";
  errorMsg?: string;
}

const KEY = "finanzas.outbox.v1";
const listeners = new Set<() => void>();
let cache: OutboxItem[] | null = null;

function load(): OutboxItem[] {
  if (cache === null) {
    try {
      cache = JSON.parse(localStorage.getItem(KEY) ?? "[]") as OutboxItem[];
    } catch {
      cache = [];
    }
  }
  return cache;
}

function save(items: OutboxItem[]) {
  cache = items;
  localStorage.setItem(KEY, JSON.stringify(items));
  listeners.forEach((l) => l());
}

/** For useSyncExternalStore: stable reference until the queue changes. */
export function getItems(): OutboxItem[] {
  return load();
}

export function subscribe(onChange: () => void): () => void {
  listeners.add(onChange);
  return () => listeners.delete(onChange);
}

function newClientId(): string {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Try the command online; on network failure (or while offline) queue it.
 *  Non-network errors (validation, 401…) are rethrown to the form. */
export async function submitOrQueue(
  command: OutboxCommand,
  args: Record<string, unknown>,
): Promise<{ queued: boolean }> {
  const clientId = newClientId();
  const fullArgs = { ...args, clientId };
  if (navigator.onLine) {
    try {
      await rpc(command, fullArgs);
      return { queued: false };
    } catch (e) {
      if (!(e instanceof NetworkError)) throw e;
    }
  }
  save([
    ...load(),
    {
      id: clientId,
      command,
      args: fullArgs,
      createdAt: new Date().toISOString(),
      status: "pending",
    },
  ]);
  return { queued: true };
}

let flushing = false;

/** Send pending items FIFO. Network failure stops the run (retried on the
 *  next 'online' event); an API error marks the item for manual review.
 *  Returns how many items synced. */
export async function flush(): Promise<number> {
  if (flushing) return 0;
  flushing = true;
  let synced = 0;
  try {
    for (const item of load().filter((i) => i.status === "pending")) {
      try {
        await rpc(item.command, item.args);
        synced++;
        save(load().filter((i) => i.id !== item.id));
      } catch (e) {
        if (e instanceof NetworkError) break;
        const msg = e instanceof Error ? e.message : String(e);
        save(
          load().map((i) =>
            i.id === item.id ? { ...i, status: "error" as const, errorMsg: msg } : i,
          ),
        );
      }
    }
  } finally {
    flushing = false;
  }
  return synced;
}

export function discard(id: string) {
  save(load().filter((i) => i.id !== id));
}

export function retryItem(id: string) {
  save(
    load().map((i) =>
      i.id === id ? { ...i, status: "pending" as const, errorMsg: undefined } : i,
    ),
  );
}
