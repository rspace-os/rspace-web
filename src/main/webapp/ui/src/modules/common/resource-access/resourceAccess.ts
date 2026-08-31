import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  type ResourceAccessDocument,
  ResourceAccessDocumentSchema,
  type ResourceGranteeDirectoryEntry,
  ResourceGranteeDirectoryEntrySchema,
} from "./schemas";

export class ResourceAccessRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`Resource access request failed with status ${status}`);
    this.status = status;
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
  const response = await fetch(`/api/v2/${resource}/${id}/access`, { headers: headers(token), signal });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(ResourceAccessDocumentSchema, (await response.json()) as unknown);
}

export async function replaceResourceAccess(
  resource: string,
  id: number,
  version: number,
  assignments: ReadonlyArray<{ granteeKey: string; role: string }>,
  token: string,
): Promise<ResourceAccessDocument> {
  const response = await fetch(`/api/v2/${resource}/${id}/access`, {
    method: "PUT",
    headers: { ...headers(token, true), "If-Match": `"${version}"` },
    body: JSON.stringify({ assignments }),
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(ResourceAccessDocumentSchema, (await response.json()) as unknown);
}

export async function leaveResource(resource: string, id: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/${resource}/${id}/access/me`, {
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
  const parameters = new URLSearchParams({ query, limit: "20" });
  const response = await fetch(`/api/v2/${resource}/${id}/access/grantees?${parameters}`, {
    headers: headers(token),
    signal,
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(v.array(ResourceGranteeDirectoryEntrySchema), (await response.json()) as unknown);
}

export async function searchBookingSettingsGrantees(
  query: string,
  token: string,
  signal?: AbortSignal,
): Promise<readonly ResourceGranteeDirectoryEntry[]> {
  const parameters = new URLSearchParams({ query, limit: "20" });
  const response = await fetch(`/api/v2/booking-settings/access-grantees?${parameters}`, {
    headers: headers(token),
    signal,
  });
  if (!response.ok) throw new ResourceAccessRequestError(response.status);
  return parseOrThrow(v.array(ResourceGranteeDirectoryEntrySchema), (await response.json()) as unknown);
}
