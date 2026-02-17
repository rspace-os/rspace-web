import { RestApiError, RestApiErrorSchema } from "@/modules/common/api/schema";
import { parse } from "@/modules/common/queries/parseOrThrow";
import {
  StoichiometryMessageErrorResponse,
  StoichiometryMessageErrorResponseSchema,
} from "@/modules/stoichiometry/schema";

export const STOICHIOMETRY_API_BASE_URL = "/api/v1";

export type TokenParams = {
  token?: string;
  getToken?: () => Promise<string>;
};

export const resolveToken = async ({ token, getToken }: TokenParams) => {
  if (token) {
    return token;
  }
  if (getToken) {
    return getToken();
  }
  throw new Error("Token is required to perform this operation");
};

export const toStoichiometryError = (
  data: unknown,
  fallbackMessage: string,
): Error => {
  const restApiError = parse(RestApiErrorSchema, data).caseOf({
    Left: () => null,
    Right: (validatedError: RestApiError) => validatedError,
  });
  if (restApiError) {
    return new Error(restApiError.message);
  }

  const messageError = parse(
    StoichiometryMessageErrorResponseSchema,
    data,
  ).caseOf({
    Left: () => null,
    Right: (validatedError: StoichiometryMessageErrorResponse) =>
      validatedError,
  });
  if (messageError) {
    return new Error(messageError.message);
  }

  return new Error(fallbackMessage);
};
