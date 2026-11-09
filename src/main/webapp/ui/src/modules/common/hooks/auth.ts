import { queryOptions, useSuspenseQuery } from "@tanstack/react-query";
import { getUiToken, secondsToExpiry, TOKEN_EXPIRY_BUFFER_SECONDS } from "@/modules/common/utils/auth";

export const queryKeys = {
  all: ["rspace.common.auth"] as const,
  oauthToken: () => [...queryKeys.all, "oauthToken"] as const,
};

/** Milliseconds until a token should be refreshed, or false for opaque/test tokens. */
export function tokenRefreshInterval(token?: string): number | false {
  if (!token) return false;
  const secondsUntilRefresh = secondsToExpiry(token) - TOKEN_EXPIRY_BUFFER_SECONDS;
  if (!Number.isFinite(secondsUntilRefresh)) return false;
  // Larger delays overflow the signed 32-bit timer limit and can trigger immediate refetches.
  return Math.min(2_147_483_647, Math.max(1_000, secondsUntilRefresh * 1_000));
}

const oauthTokenQueryOptions = queryOptions({
  queryKey: queryKeys.oauthToken(),
  // The page-wide provider mints once per page load (the shared storage key may hold a token from
  // another session context, such as before an operate-as start or release) and covers public
  // document pages too.
  queryFn: () => getUiToken(),
  // Calculate stale time based on token expiry
  // We'll consider the token stale 5 minutes before it actually expires
  staleTime: (query) => {
    const token = query.state.data;
    if (!token) return 0;
    const secondsUntilExpiry = secondsToExpiry(token);
    // Subtract the buffer time (5 minutes) to ensure we refresh before expiry
    const staleTimeSeconds = Math.max(0, secondsUntilExpiry - TOKEN_EXPIRY_BUFFER_SECONDS);
    return Number.isFinite(staleTimeSeconds) ? Math.min(2_147_483_646, staleTimeSeconds * 1000) : Infinity;
  },
  // Keep the token in cache indefinitely while the app is open
  gcTime: Infinity,
  // Stale time alone does not schedule work. Refresh while the page remains open so a mutation
  // cannot reuse a session-bound token after it expires.
  refetchInterval: (query) => tokenRefreshInterval(query.state.data),
  refetchIntervalInBackground: true,
  // Refetch in the background when the token is stale
  refetchOnMount: true,
  refetchOnWindowFocus: true,
  refetchOnReconnect: true,
  // Retry on failure
  retry: 3,
  retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
});

/**
 * Gets a token for authenticated API calls through TanStack Query. The hook uses Suspense for
 * loading states. It also uses the TanStack Query cache and automatic refetch behavior.
 *
 * The hook automatically:
 * - Caches the token and reuses it across components.
 * - Refetches the token before it expires.
 * - Keeps session-bound API v2 tokens in memory; public document page tokens also go to session storage.
 * - Integrates with React Suspense for loading states.
 * - Manages token lifetime with the TanStack Query `staleTime` option.
 *
 * Usage example:
 *
 *   // Wrap in a Suspense boundary
 *   <Suspense fallback={<div>Loading...</div>}>
 *     <MyComponent />
 *   </Suspense>
 *
 *   // Inside MyComponent:
 *   const { data: token, error } = useOauthTokenQuery();
 *
 *   // Use the token in fetch requests
 *   const response = await fetchWithUiToken(`/api/v1/maintenances`, {
 *     headers: {
 *       Authorization: "Bearer " + token,
 *     },
 *   });
 *
 * The token is automatically refetched before expiry, so you can safely use
 * the token value from the query result without worrying about staleness.
 */
export function useOauthTokenQuery() {
  return useSuspenseQuery(oauthTokenQueryOptions);
}
