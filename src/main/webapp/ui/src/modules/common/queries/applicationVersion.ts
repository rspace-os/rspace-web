import { type UseSuspenseQueryResult, useSuspenseQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

/**
 * The running RSpace version is served by the public, unauthenticated endpoint
 * `/public/version` as a plain-text string (e.g. "2.24.0").
 */
const ApplicationVersionSchema = v.string();

export const applicationVersionQueryKeys = {
  all: ["rspace.api.applicationVersion"] as const,
};

export async function getApplicationVersion(): Promise<string> {
  const response = await fetch("/public/version", {
    method: "GET",
    // Flag the request as XHR so the server does not remember it as the last
    // page the user tried to load (see the comment in `@/common/axios`).
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch application version: ${response.statusText}`);
  }

  const data: unknown = await response.text();
  return parseOrThrow(ApplicationVersionSchema, data);
}

/**
 * Fetches the running RSpace application version; see
 * {@link getApplicationVersion}.
 *
 * This is a suspense query, so the calling component must be rendered inside a
 * `<Suspense>` boundary (for the loading state) and an error boundary (for the
 * error state); in return `data` is always defined.
 *
 * The version is constant for the lifetime of a page, so the query is
 * configured to never go stale and to never be garbage collected. Combined with
 * TanStack Query's request de-duplication, this means `/public/version` is
 * requested at most once per page no matter how many components mount this hook
 * or how often the About dialog is opened and closed.
 */
export function useApplicationVersionQuery(): UseSuspenseQueryResult<string, Error> {
  return useSuspenseQuery({
    queryKey: applicationVersionQueryKeys.all,
    queryFn: getApplicationVersion,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}
