import { useSuspenseQuery } from "@tanstack/react-query";
import {
  fetchOauthToken,
  isPublicDocumentView,
  mintUiToken,
  saveStoredToken,
  secondsToExpiry,
  TOKEN_EXPIRY_BUFFER_SECONDS,
} from "@/modules/common/utils/auth";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

const queryKeys = {
  all: ["rspace.common.auth"] as const,
  oauthToken: () => [...queryKeys.all, "oauthToken"] as const,
  whoami: () => [...queryKeys.all, "whoami"] as const,
};

const API_BASE_URL = "/api/v1";

/**
 * Fetches a new OAuth token from the server.
 * This is used internally by the useOauthTokenQuery hook.
 */
async function fetchToken(): Promise<string> {
  // Authenticated pages mint a session-bound REST API v2 token, which (unlike the legacy route)
  // is issued while operating as another user. Public document pages keep the legacy route.
  const publicView = document.getElementById("public_document_view") !== null;
  const response = await fetch(
    publicView ? "/public/publicView/userform/ajax/inventoryOauthToken" : "/api/v2/oauth/tokens",
    {
      method: publicView ? "GET" : "POST",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    },
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch token: ${response.statusText}`);
  }

  if (!publicView) {
    // Session-bound tokens stay in query memory rather than shared session storage.
    return ((await response.json()) as { accessToken: string }).accessToken;
  }
  const json = (await response.json()) as { data: string };
  const newToken = json.data;
  saveStoredToken(newToken);
  return newToken;
}

/**
 * This custom hook provides a TanStack Query-powered way to get a token for
 * making calls to the API endpoints that expect an API key. This hook uses
 * Suspense for loading states and leverages TanStack Query's built-in
 * caching, automatic refetching, and stale-while-revalidate behavior.
 *
 * The hook automatically:
 * - Caches the token and reuses it across components
 * - Refetches the token when it's about to expire
 * - Persists the token to session storage
 * - Integrates with React Suspense for loading states
 * - Manages token lifetime using TanStack Query's staleTime
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
 *   const response = await fetchWithUiToken(`/api/v1/inventory/samples`, {
 *     headers: {
 *       Authorization: "Bearer " + token,
 *     },
 *   });
 *
 * The token is automatically refetched before expiry, so you can safely use
 * the token value from the query result without worrying about staleness.
 */
export function useOauthTokenQuery() {
  return useSuspenseQuery({
    queryKey: queryKeys.oauthToken(),
    // Mint once per page load: the shared storage key may hold a token from another session
    // context (for example, from before an operate-as start or release).
    queryFn: () => (isPublicDocumentView() ? fetchOauthToken(fetchToken) : mintUiToken()),
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

async function getWhoami(token?: string) {
  const response = await fetchWithUiToken(`${API_BASE_URL}/userDetails/whoami`, {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw new Error(`Failed to fetch whoami info: ${response.statusText}`);
  }

  return data;
}

export function useWhoamiQuery(token?: string) {
  return useSuspenseQuery({
    queryKey: queryKeys.whoami(),
    queryFn: async () => {
      return getWhoami(token);
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
