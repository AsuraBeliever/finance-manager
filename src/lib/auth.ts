// Session endpoints (cookie-based; the browser stores the HttpOnly cookie).

export interface AuthUser {
  id: number;
  email: string;
}

async function authPost<T>(path: string, body: Record<string, unknown>): Promise<T> {
  const res = await fetch(`/api/auth/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(data?.error ?? `Error ${res.status}`);
  }
  return (await res.json()) as T;
}

export const register = (email: string, password: string, inviteCode: string) =>
  authPost<AuthUser>("register", { email, password, inviteCode });

export const login = (email: string, password: string) =>
  authPost<AuthUser>("login", { email, password });

export const logout = () => authPost<{ ok: boolean }>("logout", {});

/** Current session's user, or null when not logged in. */
export async function me(): Promise<AuthUser | null> {
  const res = await fetch("/api/auth/me", { credentials: "same-origin" });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`Error ${res.status}`);
  return (await res.json()) as AuthUser;
}
