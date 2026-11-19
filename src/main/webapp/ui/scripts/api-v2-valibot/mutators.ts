import type { OpenApiDocument, OpenApiReferenceObject, OpenApiResponseObject } from "orval";
import { isReference, operationsOf, responsesOf } from "./openapi.ts";

/** The `apiV2Fetch.ts` mutators an operation can need instead of the default `apiV2Fetch`. */
export type MutatorOverride = "apiV2HeadersFetch" | "apiV2BlobFetch";

export interface MutatorRules {
  /** Operations whose response headers (ETag, Location) callers read. */
  headers: readonly string[];
  /** Operations that declare an ETag their callers deliberately do not read. */
  etagExclusions: readonly string[];
}

const isJson = (media: string) => {
  const type = media.split(";")[0].trim().toLowerCase();
  return type === "application/json" || type.endsWith("+json");
};

function resolveResponse(
  document: OpenApiDocument,
  response: OpenApiResponseObject | OpenApiReferenceObject,
): OpenApiResponseObject {
  if (!isReference(response)) return response;
  const ref = response.$ref;
  const name = ref.replace(/^#\/components\/responses\//, "");
  const resolved = document.components?.responses?.[name];
  if (name === ref || !resolved) throw new Error(`Unresolvable response reference ${ref}`);
  return resolveResponse(document, resolved);
}

/**
 * Chooses each operation's mutator from its declared responses. Binary successes use `apiV2BlobFetch`; the headers
 * allowlist uses `apiV2HeadersFetch`. Fails on success shapes the transport cannot represent, and on an ETag that
 * no caller would see. Remove once Orval can choose a mutator per operation:
 * https://github.com/orval-labs/orval/issues/4551
 */
export function mutatorOverrides(document: OpenApiDocument, rules: MutatorRules): Record<string, MutatorOverride> {
  const overrides: Record<string, MutatorOverride> = {};
  const known = new Set<string>();
  for (const [path, item] of Object.entries(document.paths ?? {})) {
    for (const [method, operation] of operationsOf(item)) {
      const id = operation.operationId;
      if (!id) throw new Error(`Missing operationId: ${method} ${path}`);
      known.add(id);
      let json = false;
      let binary = false;
      let empty = false;
      let etag = false;
      for (const [status, declared] of responsesOf(operation)) {
        const response = resolveResponse(document, declared);
        const headers = response.headers;
        if (
          headers &&
          typeof headers === "object" &&
          Object.keys(headers).some((name) => name.toLowerCase() === "etag")
        )
          etag = true;
        if (!status.startsWith("2")) continue;
        const media = Object.keys(response.content ?? {});
        if (media.length === 0) {
          if (status !== "204") throw new Error(`${id}: ${status} declares no content; only 204 may be bodyless`);
          empty = true;
        }
        for (const type of media) {
          if (isJson(type)) json = true;
          else binary = true;
        }
      }
      if (binary && json) throw new Error(`${id}: mixes JSON and binary successes`);
      if (binary && empty) throw new Error(`${id}: a binary success with a bodyless 204 would hide the 204 as a Blob`);
      if (rules.headers.includes(id)) {
        if (binary) throw new Error(`${id}: binary responses already return their headers`);
        overrides[id] = "apiV2HeadersFetch";
      } else if (binary) overrides[id] = "apiV2BlobFetch";
      if (etag && !binary && !rules.headers.includes(id) && !rules.etagExclusions.includes(id))
        throw new Error(`${id}: declares an ETag; add it to the headers allowlist or the ETag exclusions`);
    }
  }
  for (const id of [...rules.headers, ...rules.etagExclusions]) {
    if (!known.has(id)) throw new Error(`Mutator rule for an unknown operation: ${id}`);
  }
  return overrides;
}
