import { apiV2Json, apiV2Response } from "./apiV2Fetch";

export type { BodyType, ErrorType } from "./apiV2Fetch";

export function apiV2HeadersFetch<T>(
  url: string,
  init: RequestInit & { operationId: string },
): Promise<{ body: T; headers: Headers }>;
export function apiV2HeadersFetch<T>(url: string, init?: RequestInit): Promise<{ body: T; headers: Headers }>;
export async function apiV2HeadersFetch<T>(url: string, init?: RequestInit): Promise<{ body: T; headers: Headers }> {
  const result = await apiV2Json(url, init);
  return { body: result.body as T, headers: result.headers };
}

export function apiV2BlobFetch<_T = unknown>(
  url: string,
  init: RequestInit & { operationId: string },
): Promise<{ body: Blob; headers: Headers }>;
export function apiV2BlobFetch<_T = unknown>(
  url: string,
  init?: RequestInit,
): Promise<{ body: Blob; headers: Headers }>;
export async function apiV2BlobFetch<_T = unknown>(
  url: string,
  init?: RequestInit,
): Promise<{ body: Blob; headers: Headers }> {
  const { response } = await apiV2Response(url, init);
  return { body: await response.blob(), headers: response.headers };
}
