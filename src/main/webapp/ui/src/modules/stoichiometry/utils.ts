import type { TFunction } from "i18next";
import { type RestApiError, RestApiErrorSchema } from "@/modules/common/api/schema";
import { parse } from "@/modules/common/queries/parseOrThrow";
import {
  type StoichiometryMessageErrorResponse,
  StoichiometryMessageErrorResponseSchema,
} from "@/modules/stoichiometry/schema";

export const STOICHIOMETRY_API_BASE_URL = "/api/v1";

/**
 * Thrown by `toStoichiometryError` when the server response didn't carry a
 * usable error message. Carries a pure i18n key (plus interpolation values)
 * rather than translated text, so translation happens once, at display time,
 * via `resolveStoichiometryErrorMessage`.
 */
export class StoichiometryFallbackError extends Error {
  constructor(
    public readonly key: string,
    public readonly values?: Record<string, unknown>,
  ) {
    super(key);
  }
}

export const toStoichiometryError = (data: unknown, fallbackKey: string, values?: Record<string, unknown>): Error => {
  const restApiError = parse(RestApiErrorSchema, data).caseOf({
    Left: () => null,
    Right: (validatedError: RestApiError) => validatedError,
  });
  if (restApiError) {
    return new Error(restApiError.message);
  }

  const messageError = parse(StoichiometryMessageErrorResponseSchema, data).caseOf({
    Left: () => null,
    Right: (validatedError: StoichiometryMessageErrorResponse) => validatedError,
  });
  if (messageError) {
    return new Error(messageError.message);
  }

  return new StoichiometryFallbackError(fallbackKey, values);
};

/**
 * Resolves an error from a stoichiometry query/mutation into display text:
 * translates the key for a `StoichiometryFallbackError`, otherwise falls back
 * to the error's own message (server-provided text, already human-readable).
 */
export const resolveStoichiometryErrorMessage = (error: unknown, t: TFunction, fallback: string): string => {
  if (error instanceof StoichiometryFallbackError) {
    // t's overloads infer their return type from a literal key; a value typed
    // as `string` defeats that inference, so call through a simpler view of
    // the same function (see the analogous cast in lazyT.ts).
    const translate = t as (key: string, options?: Record<string, unknown>) => string;
    return translate(error.key, error.values);
  }
  return error instanceof Error ? error.message : fallback;
};
