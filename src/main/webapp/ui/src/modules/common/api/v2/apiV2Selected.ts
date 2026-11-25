import type { IsStringLiteral, IsTuple, IsUnion, SetRequired } from "type-fest";
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

/** True for a fixed-length tuple whose every element is one string literal key. */
type IsLiteralTuple<F extends readonly unknown[]> =
  IsTuple<F> extends true
    ? false extends {
        [I in keyof F]: IsStringLiteral<F[I]> extends true ? (IsUnion<F[I]> extends true ? false : true) : false;
      }[number]
      ? false
      : true
    : false;

/**
 * Document `D` after a sparse `fields` read of `F`: each requested field in the guaranteed set `G` is present
 * (`undefined` removed, `null` kept where the schema allows it). Other fields stay optional. Only a literal tuple of
 * keys narrows; a widened array might hold only some of its member types, so it leaves the plain document.
 */
export type ApiV2SelectedDocument<D, G extends string, F extends readonly string[]> =
  IsLiteralTuple<F> extends true ? SetRequired<D, Extract<keyof D, F[number] & G>> : D;

/** A resource's `*Read` document after a sparse `fields` read, narrowed by the resource's read guarantees. */
export type ApiV2Selected<R extends ApiV2ReadResource, F extends readonly string[]> = ApiV2SelectedDocument<
  ApiV2ReadDocument<R>,
  // Extract keeps the generated set's member type assignable to string while R is still generic.
  Extract<ApiV2GuaranteedReadFields[R], string>,
  F
>;

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
