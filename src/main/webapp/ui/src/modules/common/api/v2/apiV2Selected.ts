import { ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import type { apiV2Resources } from "./generated/apiV2Resources";
import { type ApiV2GuaranteedReadFields, apiV2GuaranteedReadFields } from "./generated/guaranteedReadFields";

/** A resource whose `*Read` documents carry read guarantees. */
export type ApiV2ReadResource = keyof ApiV2GuaranteedReadFields & keyof typeof apiV2Resources;

/** The `*Read` document a resource's single read returns. */
export type ApiV2ReadDocument<R extends ApiV2ReadResource> = (typeof apiV2Resources)[R] extends {
  get: { request: (...args: never[]) => Promise<infer D> };
}
  ? D
  : never;

type IsUnion<T, U = T> = T extends unknown ? ([U] extends [T] ? false : true) : never;
/** True for a fixed-length tuple whose every element is one literal key. */
type IsLiteralTuple<F extends readonly unknown[]> = F extends readonly []
  ? true
  : F extends readonly [infer Head, ...infer Rest]
    ? string extends Head
      ? false
      : true extends IsUnion<Head>
        ? false
        : IsLiteralTuple<Rest>
    : false;

type Simplify<T> = { [K in keyof T]: T[K] } & {};
type Present<T, K extends PropertyKey> = Simplify<Omit<T, K> & Required<Pick<T, Extract<keyof T, K>>>>;

/**
 * A resource's `*Read` document after a sparse `fields` read: each requested field that the resource guarantees is
 * present (`undefined` removed, `null` kept where the schema allows it). Other fields stay optional. Only a literal
 * tuple of keys narrows; a widened array might hold only some of its member types, so it leaves the plain document.
 */
export type ApiV2Selected<R extends ApiV2ReadResource, F extends readonly string[]> =
  IsLiteralTuple<F> extends true
    ? Present<ApiV2ReadDocument<R>, Extract<F[number], ApiV2GuaranteedReadFields[R]>>
    : ApiV2ReadDocument<R>;

/** The `fields[...]` query value for an `as const` tuple, so the request and `ApiV2Selected` cannot drift apart. */
export function apiV2Fields(fields: readonly string[]): string {
  return fields.join(",");
}

/**
 * Asserts at the fetch boundary that a sparse read returned every requested guaranteed field. A missing one means the
 * response broke its contract, so it fails as `invalidBody`, like any other schema failure.
 */
export function assertApiV2Selected<R extends ApiV2ReadResource, const F extends readonly string[]>(
  resource: R,
  document: ApiV2ReadDocument<R> & object,
  fields: F,
): asserts document is ApiV2Selected<R, F> & ApiV2ReadDocument<R> & object {
  const guaranteed: ReadonlySet<string> = apiV2GuaranteedReadFields[resource];
  const missing = fields.filter((field) => guaranteed.has(field) && Reflect.get(document, field) === undefined);
  if (missing.length > 0) {
    throw new ApiV2UnexpectedResponseError("invalidBody", {
      cause: new Error(`Missing guaranteed ${resource} fields: ${missing.join(", ")}`),
    });
  }
}
