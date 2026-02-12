import {
  GroupInfoSchema,
} from "@/modules/groups/schema";
import { parse, parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { Either } from "purify-ts/Either";
import { useSuspenseQuery } from "@tanstack/react-query";
import { RestApiError, RestApiErrorSchema } from "@/modules/common/api/schema";

const API_BASE_URL = "/api/v1";

export const groupQueryKeys = {
  all: ["rspace.config.groups"] as const,
  groupById: (id: string) => [...groupQueryKeys.all, "byId", id] as const,
};

export async function getGroupById(
  id: string,
  { token }: { token: string },
) {
  const response = await fetch(`${API_BASE_URL}/groups/${id}`, {
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
      .orDefault(new Error(`Failed to fetch group: ${response.statusText}`));
  }

  return parseOrThrow(GroupInfoSchema, data);
}

export function useGetGroupByIdQuery({id, token}: { id: string, token: string }) {
  return useSuspenseQuery({
    queryKey: groupQueryKeys.groupById(id),
    queryFn: () => getGroupById(id, { token }),
  });
}