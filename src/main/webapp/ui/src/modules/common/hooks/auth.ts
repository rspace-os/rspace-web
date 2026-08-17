import { useSuspenseQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  getStoredToken,
  isExpiringSoon,
  saveStoredToken,
  secondsToExpiry,
  TOKEN_EXPIRY_BUFFER_SECONDS,
} from "@/modules/common/utils/auth";

const queryKeys = {
  all: ["rspace.common.auth"] as const,
  oauthToken: (useRestApiV2: boolean) => [...queryKeys.all, "oauthToken", useRestApiV2 ? "v2" : "legacy"] as const,
};

/**
 * Fetches a new OAuth token from the server.
 * This is used internally by the useOauthTokenQuery hook.
 */
const LegacyOauthTokenSchema = v.object({ data: v.string() });
const RestApiV2OauthTokenSchema = v.object({ accessToken: v.string() });

export async function fetchToken(useRestApiV2 = false): Promise<string> {
  const response = await fetch(useRestApiV2 ? "/api/v2/oauth/tokens" : "/userform/ajax/inventoryOauthToken", {
    method: useRestApiV2 ? "POST" : "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch token: ${response.statusText}`);
  }

  const data: unknown = await response.json();
  const newToken = useRestApiV2
    ? parseOrThrow(RestApiV2OauthTokenSchema, data).accessToken
    : parseOrThrow(LegacyOauthTokenSchema, data).data;
  // Session-bound v2 tokens belong to one browser/run-as context and must not replace the legacy
  // bearer token that older API clients read from shared session storage.
  if (!useRestApiV2) saveStoredToken(newToken);
  return newToken;
}

/**
 * Gets a token for authenticated API calls through TanStack Query. The hook uses Suspense for
 * loading states. It also uses the TanStack Query cache and automatic refetch behavior.
 *
 * The hook automatically:
 * - Caches the token and reuses it across components.
 * - Refetches the token before it expires.
 * - Persists legacy tokens to session storage.
 * - Keeps session-bound API v2 tokens in query memory.
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
 *   const response = await fetch(`/api/v2/maintenances`, {
 *     headers: {
 *       Authorization: "Bearer " + token,
 *     },
 *   });
 *
 * The token is automatically refetched before expiry, so you can safely use
 * the token value from the query result without worrying about staleness.
 */
export function useOauthTokenQuery({ useRestApiV2 = false }: { useRestApiV2?: boolean } = {}) {
  return useSuspenseQuery({
    queryKey: queryKeys.oauthToken(useRestApiV2),
    queryFn: async () => {
      // V2 tokens are bound to the current run-as identity and its rotating browser-session
      // context. Mint once per page load so a run-as start/release redirect cannot reuse a stale
      // token from session storage.
      if (useRestApiV2) {
        return fetchToken(true);
      }
      // First, check if we have a valid token in session storage
      const savedToken = getStoredToken();
      if (savedToken && !isExpiringSoon(savedToken)) {
        return savedToken;
      }
      // If no valid token exists, fetch a new one
      return fetchToken(useRestApiV2);
    },
    // Calculate stale time based on token expiry
    // We'll consider the token stale 5 minutes before it actually expires
    staleTime: (query) => {
      const token = query.state.data;
      if (!token) return 0;
      const secondsUntilExpiry = secondsToExpiry(token);
      // Subtract the buffer time (5 minutes) to ensure we refresh before expiry
      const staleTimeSeconds = Math.max(0, secondsUntilExpiry - TOKEN_EXPIRY_BUFFER_SECONDS);
      return staleTimeSeconds * 1000; // Convert to milliseconds
    },
    // Keep the token in cache indefinitely while the app is open
    gcTime: Infinity,
    // Refetch in the background when the token is stale
    refetchOnMount: true,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    // Retry on failure
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}
