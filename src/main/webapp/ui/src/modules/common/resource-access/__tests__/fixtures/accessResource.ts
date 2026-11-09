import * as v from "valibot";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import {
  ApiV2ProblemSchema,
  ResourceAccessDocumentSchema,
  ResourceGranteeDirectoryDocumentSchema,
} from "@/modules/common/api/v2/generated/valibot/schemas";
import type { AccessMutationFactories, AccessResource } from "../../resourceAccess";

async function response(url: string, options?: RequestInit) {
  const result = await fetch(url, options);
  if (!result.ok) {
    const body: unknown = await result.json().catch(() => undefined);
    const problem = v.safeParse(ApiV2ProblemSchema, body);
    throw new ApiV2ProblemError(
      result.status,
      problem.success ? problem.output.code : undefined,
      result.statusText,
      problem.success ? problem.output : undefined,
    );
  }
  return result;
}

async function json<TSchema extends v.GenericSchema>(
  url: string,
  schema: TSchema,
  options?: RequestInit,
): Promise<v.InferOutput<TSchema>> {
  return v.parse(schema, await (await response(url, options)).json());
}

// The access contract exists before any collection opts in to its routes at layer 11.
export const accessResource = {
  name: "booking-configurations",
  access: {
    request: (id, options) =>
      json(`/api/v2/booking-configurations/${id}/access`, ResourceAccessDocumentSchema, options),
    queryKey: (id) => [`/api/v2/booking-configurations/${id}/access`] as const,
  },
  replaceAccess: (id, data, headers, options) =>
    json(`/api/v2/booking-configurations/${id}/access`, ResourceAccessDocumentSchema, {
      ...options,
      method: "PUT",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(data),
    }),
  leaveAccess: async (id, options) => {
    await response(`/api/v2/booking-configurations/${id}/access/me`, { ...options, method: "DELETE" });
  },
  grantees: {
    request: (id, params, options) =>
      json(
        `/api/v2/booking-configurations/${id}/access/grantees?${new URLSearchParams({ q: params.q, limit: String(params.limit ?? 20) })}`,
        ResourceGranteeDirectoryDocumentSchema,
        options,
      ),
    queryKey: (id, params) => [`/api/v2/booking-configurations/${id}/access/grantees`, params] as const,
  },
} satisfies AccessResource;

export const accessMutationOptions = {
  replaceAccess: (_client, options) => ({ ...options?.mutation }),
  leaveAccess: (_client, options) => ({ ...options?.mutation }),
} satisfies AccessMutationFactories;
