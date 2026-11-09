import type { QueryClient, QueryKey, UseMutationOptions } from "@tanstack/react-query";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import type { ApiV2ReadOperation, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";
import * as bookingSettings from "@/modules/common/api/v2/generated/booking-settings/booking-settings";
import type { ResourceAccessDocument } from "@/modules/common/api/v2/generated/models/resourceAccessDocument";
import type { ResourceAccessReplacement } from "@/modules/common/api/v2/generated/models/resourceAccessReplacement";
import type { ResourceGranteeDirectoryDocument } from "@/modules/common/api/v2/generated/models/resourceGranteeDirectoryDocument";
import { accessDocument, directoryEntry } from "./domain";

export type AccessResource = ApiV2ResourceEntry<
  string,
  {
    access: ApiV2ReadOperation<
      (id: number, options?: RequestInit) => Promise<ResourceAccessDocument>,
      (id: number) => QueryKey
    >;
    replaceAccess: (
      id: number,
      data: ResourceAccessReplacement,
      headers: { "If-Match": string },
      options?: RequestInit,
    ) => Promise<ResourceAccessDocument>;
    leaveAccess: (id: number, options?: RequestInit) => Promise<void>;
    grantees: ApiV2ReadOperation<
      (
        id: number,
        params: { q: string; limit?: number },
        options?: RequestInit,
      ) => Promise<ResourceGranteeDirectoryDocument>,
      (id: number, params?: { q: string; limit?: number }) => QueryKey
    >;
  }
>;

export type AccessReplacementVariables = {
  id: number;
  data: ResourceAccessReplacement;
  headers: { "If-Match": string };
};

/** Resource callers supply generated factories so their invalidation rules stay with the operations. */
export type AccessMutationFactories = {
  replaceAccess: (
    client: QueryClient,
    options?: { mutation?: UseMutationOptions<ResourceAccessDocument, ErrorType<unknown>, AccessReplacementVariables> },
  ) => UseMutationOptions<ResourceAccessDocument, ErrorType<unknown>, AccessReplacementVariables>;
  leaveAccess: (
    client: QueryClient,
    options?: { mutation?: UseMutationOptions<void, ErrorType<unknown>, { id: number }> },
  ) => UseMutationOptions<void, ErrorType<unknown>, { id: number }>;
};

export async function fetchResourceAccess(resource: Pick<AccessResource, "access">, id: number, signal?: AbortSignal) {
  return accessDocument(await resource.access.request(id, { signal }));
}

export async function replaceResourceAccess(
  resource: Pick<AccessResource, "replaceAccess">,
  id: number,
  version: number,
  assignments: ReadonlyArray<{ granteeKey: string; role: string }>,
) {
  return accessDocument(
    await resource.replaceAccess(id, { assignments: [...assignments] }, { "If-Match": `"${version}"` }),
  );
}

export async function leaveResource(
  resource: ApiV2ResourceEntry<string, Pick<AccessResource, "leaveAccess">>,
  id: number,
): Promise<void> {
  return resource.leaveAccess(id);
}

export async function searchResourceGrantees(
  resource: Pick<AccessResource, "grantees">,
  id: number,
  query: string,
  signal?: AbortSignal,
) {
  const document = await resource.grantees.request(id, { q: query, limit: 20 }, { signal });
  return (document.docs ?? []).map(directoryEntry);
}

export async function searchBookingSettingsGrantees(query: string, signal?: AbortSignal) {
  const document = await bookingSettings.searchBookingSettingsAccessGrantees({ q: query, limit: 20 }, { signal });
  return (document.docs ?? []).map(directoryEntry);
}
