import * as v from "valibot";
import axios from "@/common/axios";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
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
  const { data } = await axios.get<unknown>("/public/version");
  return parseOrThrow(ApplicationVersionSchema, data);
}

/**
 * Fetches the running RSpace application version; see
 * {@link getApplicationVersion}.
 *
 * The version is constant for the lifetime of a page, so the query is
 * configured to never go stale. Combined with TanStack Query's request
 * de-duplication and caching, this means `/public/version` is requested at
 * most once per page no matter how many components mount this hook or how
 * often the About dialog is opened and closed.
 */
export function useApplicationVersionQuery(): UseQueryResult<string, Error> {
  return useQuery({
    queryKey: applicationVersionQueryKeys.all,
    queryFn: getApplicationVersion,
    staleTime: Infinity,
  });
}
