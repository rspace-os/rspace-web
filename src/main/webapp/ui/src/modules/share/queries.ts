import { parse, parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { Either } from "purify-ts/Either";
import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { RestApiError, RestApiErrorSchema } from "@/modules/common/api/schema";
import {
  ShareSearchResponseSchema,
  type ShareSearchResponse,
} from "@/modules/share/schema";
import { getCommonGroupsInShares } from "@/modules/share/services/shareGroups";

const API_BASE_URL = "/api/v1";

export type ShareOrderBy =
  | "lastModified asc"
  | "lastModified desc"
  | "name asc"
  | "name desc"
  | "created asc"
  | "created desc";

export type ShareListingParams = {
  pageNumber?: number;
  pageSize?: number;
  orderBy?: ShareOrderBy;
  query?: string;
  sharedItemIds?: ReadonlyArray<string>;
};

export const shareQueryKeys = {
  all: ["rspace.api.share"] as const,
  listing: (params: ShareListingParams) =>
    [...shareQueryKeys.all, "listing", params] as const,
  commonGroups: (params: ShareListingParams) =>
    [...shareQueryKeys.all, "commonGroups", params] as const,
};

const buildShareListingSearchParams = (
  params: ShareListingParams,
): URLSearchParams => {
  const searchParams = new URLSearchParams();

  if (params.pageNumber !== undefined) {
    searchParams.set("pageNumber", String(params.pageNumber));
  }

  if (params.pageSize !== undefined) {
    searchParams.set("pageSize", String(params.pageSize));
  }

  if (params.orderBy) {
    searchParams.set("orderBy", params.orderBy);
  }

  if (params.query) {
    searchParams.set("query", params.query);
  }

  if (params.sharedItemIds && params.sharedItemIds.length > 0) {
    searchParams.set("sharedItemIds", params.sharedItemIds.join(","));
  }

  return searchParams;
};

export async function getShareListing(
  params: ShareListingParams = {},
  { token }: { token: string },
): Promise<ShareSearchResponse> {
  const searchParams = buildShareListingSearchParams(params);
  const url = searchParams.toString()
    ? `${API_BASE_URL}/share?${searchParams.toString()}`
    : `${API_BASE_URL}/share`;
  const response = await fetch(url, {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
      Authorization: `Bearer ${token}`,
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    // Try to parse and throw typed error
    const errorResult: Either<Error, RestApiError> = parse(
      RestApiErrorSchema,
      data,
    );
    throw errorResult
      .map((validatedError: RestApiError) => new Error(validatedError?.message))
      .orDefault(
        new Error(`Failed to fetch shared items: ${response.statusText}`),
      );
  }

  return parseOrThrow(ShareSearchResponseSchema, data);
}

export function useShareListingQuery({
  params,
  token,
}: {
  params?: ShareListingParams;
  token: string;
}) {
  return useSuspenseQuery({
    queryKey: shareQueryKeys.listing(params ?? {}),
    queryFn: () => getShareListing(params ?? {}, { token }),
  });
}

export function useCommonGroupsShareListingQuery({
  params,
  token,
  enabled = true,
}: {
  params?: ShareListingParams;
  token: string;
  enabled?: boolean;
}) {
  return useQuery({
    queryKey: shareQueryKeys.commonGroups(params ?? {}),
    queryFn: () => getCommonGroupsInShares(params ?? {}, { token }),
    enabled,
  });
}
