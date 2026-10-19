import { describe, expect, test } from "vitest";

import { getApiErrorDetails, getErrorMessage } from "../error";

describe("getErrorMessage", () => {
  test("should extract message from axios response data", () => {
    const error = {
      response: {
        data: {
          message: "Network error occurred",
        },
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe("Network error occurred");
  });
  test("should extract exceptionMessage from axios response data", () => {
    const error = {
      response: {
        data: {
          exceptionMessage: "Something went wrong: \ndescription too long, should be max 250 chars\n\n",
        },
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe(
      "Something went wrong: \ndescription too long, should be max 250 chars\n\n",
    );
  });
  test("should prefer message over exceptionMessage when both exist", () => {
    const error = {
      response: {
        data: {
          message: "Primary error message",
          exceptionMessage: "Secondary exception message",
        },
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe("Primary error message");
  });
  test("should extract message from Error object", () => {
    const error = new Error("Standard error message");
    expect(getErrorMessage(error, "fallback")).toBe("Standard error message");
  });
  test("should return fallback for non-error objects", () => {
    const error = { someOtherProperty: "value" };
    expect(getErrorMessage(error, "fallback message")).toBe("fallback message");
  });
  test("should return fallback for null", () => {
    expect(getErrorMessage(null, "fallback message")).toBe("fallback message");
  });
  test("should return fallback for undefined", () => {
    expect(getErrorMessage(undefined, "fallback message")).toBe("fallback message");
  });
  test("should return fallback for string", () => {
    expect(getErrorMessage("some string", "fallback message")).toBe("fallback message");
  });
  test("should return fallback for number", () => {
    expect(getErrorMessage(42, "fallback message")).toBe("fallback message");
  });
  test("should handle axios response with non-string message", () => {
    const error = {
      response: {
        data: {
          message: 123,
        },
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });
  test("should handle axios response with non-string exceptionMessage", () => {
    const error = {
      response: {
        data: {
          exceptionMessage: { nested: "object" },
        },
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });
  test("should handle incomplete axios response structure", () => {
    const error = {
      response: {
        status: 500,
      },
    };
    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });
  test("should handle object that looks like Error but isn't", () => {
    const error = {
      message: "fake error message",
    };
    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });
});

describe("getApiErrorDetails", () => {
  const axiosErr = (errors: Array<string>, message?: string) => ({
    response: { data: { message, errors } },
  });

  /** These cases are not about how the index is worded, only about the reason. */
  const noIndex = (reason: string) => reason;

  test("returns an error with its path prefix stripped", () => {
    const error = axiosErr(
      ["origins[0].amountTaken: Cannot take more from an origin than it currently holds"],
      "Errors detected: 1",
    );
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual([
      "Cannot take more from an origin than it currently holds",
    ]);
  });

  test("shows every error, skipping blank ones", () => {
    const error = axiosErr(["origins[0].id: first", "", "origins[1].id: second"], "Errors detected: 2");
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["first", "second"]);
  });

  test("names which origin failed, so a Pool rejection is actionable", () => {
    const error = axiosErr(
      ["origins[3].amountTaken: Cannot take more from an origin than it currently holds"],
      "Errors detected: 1",
    );
    expect(getApiErrorDetails(error, "fallback", (reason, index) => `${reason} [#${index}]`)).toEqual([
      "Cannot take more from an origin than it currently holds [#4]",
    ]);
  });

  test("adds no origin marker to an error that is not about an origin", () => {
    const error = axiosErr(["newSample.name: This field is too long"]);
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["This field is too long"]);
  });

  test("keeps an error that has no path prefix", () => {
    const error = axiosErr(["Something went wrong"]);
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Something went wrong"]);
  });

  test("falls back to the message when the only error is blank", () => {
    const error = axiosErr([""], "Edit conflict");
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Edit conflict"]);
  });

  test("falls back when an error is nothing but a field path", () => {
    const error = axiosErr(["origins[0].id:"], "Errors detected: 1");
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Errors detected: 1"]);
  });

  test("does not strip a leading word that is not a field path", () => {
    const error = axiosErr(["Warning: stock is low"]);
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Warning: stock is low"]);
  });

  test("falls back to the message when the errors array is empty", () => {
    const error = axiosErr([], "Errors detected: 0");
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Errors detected: 0"]);
  });

  test("falls back to the message when there is no errors array", () => {
    const error = { response: { data: { message: "Plain failure" } } };
    expect(getApiErrorDetails(error, "fallback", noIndex)).toEqual(["Plain failure"]);
  });

  test("falls back for a non-Axios error", () => {
    expect(getApiErrorDetails(new Error("boom"), "fallback", noIndex)).toEqual(["boom"]);
    expect(getApiErrorDetails(null, "fallback", noIndex)).toEqual(["fallback"]);
  });
});
