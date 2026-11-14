import { notifyManager, useQueryClient } from "@tanstack/react-query";
import { useCallback, useSyncExternalStore } from "react";
import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";

export type QueryIdentityScope = readonly [string | number | null, number | null];

export function queryIdentityScope(
  user: Pick<ApiV2CurrentUser, "id" | "session"> | undefined,
  fallback?: string | number,
): QueryIdentityScope {
  // /users/me identifies the effective subject; the originating actor is not exposed.
  return [user?.id ?? fallback ?? null, user?.session?.operatedAs ? user.id : null];
}

export function useQueryIdentityScope(fallback?: string | number): QueryIdentityScope {
  const client = useQueryClient();
  const subscribe = useCallback(
    (onChange: () => void) => client.getQueryCache().subscribe(notifyManager.batchCalls(onChange)),
    [client],
  );
  const snapshot = useCallback(() => client.getQueryData<ApiV2CurrentUser>(getGetCurrentUserQueryKey()), [client]);
  const user = useSyncExternalStore(subscribe, snapshot, snapshot);
  return queryIdentityScope(user, fallback);
}
