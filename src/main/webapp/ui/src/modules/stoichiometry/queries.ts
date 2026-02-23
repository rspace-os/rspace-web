import { useSuspenseQuery } from "@tanstack/react-query";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  StoichiometryResponse,
  StoichiometryResponseSchema,
} from "@/modules/stoichiometry/schema";
import {
  resolveToken,
  STOICHIOMETRY_API_BASE_URL,
  TokenParams,
  toStoichiometryError,
} from "@/modules/stoichiometry/utils";

export type GetStoichiometryParams = {
  stoichiometryId: number;
  revision?: number;
  token: string;
};

type UseGetStoichiometryQueryTokenParams =
  | { token: string; getToken?: never }
  | { token?: never; getToken: NonNullable<TokenParams["getToken"]> };

export type UseGetStoichiometryQueryParams = Omit<
  GetStoichiometryParams,
  "token"
> &
  UseGetStoichiometryQueryTokenParams;

export const stoichiometryQueryKeys = {
  all: ["rspace.api.stoichiometry"] as const,
  byId: (stoichiometryId: number, revision?: number) =>
    [
      ...stoichiometryQueryKeys.all,
      "byId",
      stoichiometryId,
      revision ?? "latest",
    ] as const,
};

export async function getStoichiometry({
  stoichiometryId,
  revision,
  token,
}: GetStoichiometryParams): Promise<StoichiometryResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set("stoichiometryId", String(stoichiometryId));
  if (revision !== undefined) {
    searchParams.set("revision", String(revision));
  }

  const response = await fetch(
    `${STOICHIOMETRY_API_BASE_URL}/stoichiometry?${searchParams.toString()}`,
    {
      method: "GET",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${token}`,
      },
    },
  );

  const data: unknown = await response.json();

  if (!response.ok) {
    throw toStoichiometryError(
      data,
      `Failed to fetch stoichiometry: ${response.statusText}`,
    );
  }

  return parseOrThrow(StoichiometryResponseSchema, data);
}

export function useGetStoichiometryQuery({
  stoichiometryId,
  revision,
  token,
  getToken,
}: UseGetStoichiometryQueryParams) {
  return useSuspenseQuery({
    queryKey: stoichiometryQueryKeys.byId(stoichiometryId, revision),
    queryFn: async () =>
      getStoichiometry({
        stoichiometryId,
        revision,
        token: await resolveToken({ token, getToken }),
      }),
    retry: false,
  });
}
