import { parse } from "@/modules/common/queries/parseOrThrow";
import { RestApiError, RestApiErrorSchema } from "@/modules/common/api/schema";

export const WORKSPACE_API_BASE_URL = "/workspace";

function getWorkspaceAjaxErrorMessage(data: unknown): string | null {
  if (typeof data !== "object" || data === null) {
    return null;
  }

  const response = data as {
    error?: unknown;
    errorMsg?: unknown;
  };

  for (const candidate of [response.error, response.errorMsg]) {
    if (typeof candidate === "string") {
      return candidate;
    }

    if (typeof candidate !== "object" || candidate === null) {
      continue;
    }

    const errorMessages = (candidate as { errorMessages?: unknown }).errorMessages;
    if (!Array.isArray(errorMessages)) {
      continue;
    }

    const messages = errorMessages
      .map((errorMessage) => {
        if (typeof errorMessage === "string") {
          return errorMessage;
        }

        if (typeof errorMessage !== "object" || errorMessage === null) {
          return null;
        }

        const typedErrorMessage = errorMessage as {
          defaultMessage?: unknown;
          message?: unknown;
          errorCode?: unknown;
          field?: unknown;
        };

        for (const value of [
          typedErrorMessage.defaultMessage,
          typedErrorMessage.message,
          typedErrorMessage.errorCode,
          typedErrorMessage.field,
        ]) {
          if (typeof value === "string" && value.length > 0) {
            return value;
          }
        }

        return null;
      })
      .filter((message): message is string => Boolean(message));

    if (messages.length > 0) {
      return messages.join("; ");
    }
  }

  return null;
}

export const toWorkspaceError = (
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

  const ajaxErrorMessage = getWorkspaceAjaxErrorMessage(data);
  if (ajaxErrorMessage) {
    return new Error(ajaxErrorMessage);
  }

  return new Error(fallbackMessage);
};

