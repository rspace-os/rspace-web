import { queryOptions } from "@tanstack/react-query";
import * as v from "valibot";
import { apiV2Root } from "@/modules/common/queries/apiV2QueryKeys";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import {
  type ResourceAccessDocument,
  ResourceAccessDocumentSchema,
  type ResourceGranteeDirectoryEntry,
  ResourceGranteeDirectoryEntrySchema,
} from "./schemas";

export class ResourceAccessRequestError extends Error {
  readonly status: number;
  readonly code: string | null;

  constructor(status: number, code: string | null = null) {
    super(`Resource access request failed with status ${status}`);
    this.status = status;
    this.code = code;
  }
}

async function requestError(response: Response): Promise<ResourceAccessRequestError> {
  try {
    const body = (await response.json()) as { code?: unknown; errorCode?: unknown };
    const code = typeof body.code === "string" ? body.code : body.errorCode;
    return new ResourceAccessRequestError(response.status, typeof code === "string" ? code : null);
  } catch {
    return new ResourceAccessRequestError(response.status);
  }
}

function headers(token: string, json = false): HeadersInit {
  return {
    Authorization: `Bearer ${token}`,
    ...(json ? { "Content-Type": "application/json" } : {}),
  };
}

export async function fetchResourceAccess(
  resource: string,
  id: number,
  token: string,
  signal?: AbortSignal,
): Promise<ResourceAccessDocument> {
  const response = await fetchWithUiToken(`/api/v2/${resource}/${id}/access`, { headers: headers(token), signal });
  if (!response.ok) throw await requestError(response);
  return parseOrThrow(ResourceAccessDocumentSchema, (await response.json()) as unknown);
}

export async function replaceResourceAccess(
  resource: string,
  id: number,
  version: number,
  assignments: ReadonlyArray<{ granteeKey: string; role: string }>,
  token: string,
): Promise<ResourceAccessDocument> {
  const response = await fetchWithUiToken(`/api/v2/${resource}/${id}/access`, {
    method: "PUT",
    headers: { ...headers(token, true), "If-Match": `"${version}"` },
    body: JSON.stringify({ assignments }),
  });
  if (!response.ok) throw await requestError(response);
  return parseOrThrow(ResourceAccessDocumentSchema, (await response.json()) as unknown);
}

export async function leaveResource(resource: string, id: number, token: string): Promise<void> {
  const response = await fetchWithUiToken(`/api/v2/${resource}/${id}/access/me`, {
    method: "DELETE",
    headers: headers(token),
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
}

export async function searchResourceGrantees(
  resource: string,
  id: number,
  query: string,
  token: string,
  signal?: AbortSignal,
): Promise<readonly ResourceGranteeDirectoryEntry[]> {
  const parameters = new URLSearchParams({ q: query, limit: "20" });
  const response = await fetchWithUiToken(`/api/v2/${resource}/${id}/access/grantees?${parameters}`, {
    headers: headers(token),
    signal,
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(
    v.object({ docs: v.array(ResourceGranteeDirectoryEntrySchema) }),
    (await response.json()) as unknown,
  ).docs;
}

export async function searchBookingSettingsGrantees(
  query: string,
  token: string,
  signal?: AbortSignal,
): Promise<readonly ResourceGranteeDirectoryEntry[]> {
  const parameters = new URLSearchParams({ q: query, limit: "20" });
  const response = await fetchWithUiToken(`/api/v2/booking-settings/access-grantees?${parameters}`, {
    headers: headers(token),
    signal,
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(
    v.object({ docs: v.array(ResourceGranteeDirectoryEntrySchema) }),
    (await response.json()) as unknown,
  ).docs;
}
export const resourceAccessQueries = {
  all: (resource: string) => apiV2Root(resource),
  access: (resource: string, resourceId: number) => [...apiV2Root(resource), resourceId, "access"] as const,
  document: (resource: string, resourceId: number, token: string, scope: unknown) =>
    queryOptions({
      queryKey: [...resourceAccessQueries.access(resource, resourceId), scope] as const,
      queryFn: ({ signal }) => fetchResourceAccess(resource, resourceId, token, signal),
    }),
  grantees: (resource: string, resourceId: number, query: string, token: string, scope: unknown) =>
    queryOptions({
      queryKey: [...apiV2Root(resource), resourceId, "access-grantees", scope, query] as const,
      queryFn: ({ signal }) => searchResourceGrantees(resource, resourceId, query, token, signal),
    }),
};
