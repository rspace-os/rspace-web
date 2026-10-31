import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useSyncExternalStore } from "react";
import { apiV2Root } from "./apiV2QueryKeys";
import type { CurrentUser } from "./currentUser";

export type QueryIdentityScope = readonly [string | number | null, number | null];
const currentUserKey = [...apiV2Root("users"), "me"] as const;

export function queryIdentityScope(
  user: Pick<CurrentUser, "id" | "session"> | undefined,
  fallback?: string | number,
): QueryIdentityScope {
  // /users/me identifies the effective subject; the originating actor is not exposed.
  return [user?.id ?? fallback ?? null, user?.session?.operatedAs ? user.id : null];
}

export function useQueryIdentityScope(fallback?: string | number): QueryIdentityScope {
  const client = useQueryClient();
  const subscribe = useCallback((onChange: () => void) => client.getQueryCache().subscribe(onChange), [client]);
  const snapshot = useCallback(() => client.getQueryData<CurrentUser>(currentUserKey), [client]);
  const user = useSyncExternalStore(subscribe, snapshot, snapshot);
  return queryIdentityScope(user, fallback);
}
