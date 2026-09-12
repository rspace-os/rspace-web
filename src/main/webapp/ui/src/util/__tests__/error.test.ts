import { describe, expect, test } from "vitest";

import { getApiErrorDetail, getErrorMessage } from "../error";

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

describe("getApiErrorDetail", () => {
  // A field-scoped 400 from the Inventory API puts "Errors detected: 1" in `message` and the actual
  // reason in `errors[0]`, prefixed by the path it applies to. Showing only `message` told the user
  // nothing they could act on.
  test("returns the first error, with its path prefix stripped", () => {
    const error = {
      response: {
        data: {
          message: "Errors detected: 1",
          errors: ["origins[0].amountTaken: Cannot take more from an origin than it currently holds"],
        },
      },
    };
    expect(getApiErrorDetail(error, "fallback")).toBe("Cannot take more from an origin than it currently holds");
  });

  test("shows only the first of several errors", () => {
    const error = {
      response: {
        data: {
          message: "Errors detected: 2",
          errors: ["origins[0].id: first", "origins[1].id: second"],
        },
      },
    };
    expect(getApiErrorDetail(error, "fallback")).toBe("first");
  });

  test("drops the origin index when the caller supplies no way to word it", () => {
    const error = {
      response: {
        data: { message: "Errors detected: 1", errors: ["origins[3].amountTaken: Cannot take more"] },
      },
    };
    expect(getApiErrorDetail(error, "fallback")).toBe("Cannot take more");
  });

  test("names which origin failed, so a Pool rejection is actionable", () => {
    // Stripping the whole path left "Cannot take more than the subsample holds" with no indication
    // which of five pooled origins was at fault (parallel review, FE11). Reported 1-based: the user
    // reads it, nothing sends it back.
    const error = {
      response: {
        data: {
          message: "Errors detected: 1",
          errors: ["origins[3].amountTaken: Cannot take more from an origin than it currently holds"],
        },
      },
    };
    // The wording is the caller's: this module has no `t`, and appending English to a reason the
    // server already localized is exactly the bug (parallel review, A4).
    expect(getApiErrorDetail(error, "fallback", (reason, index) => `${reason} [#${index}]`)).toBe(
      "Cannot take more from an origin than it currently holds [#4]",
    );
  });

  test("adds no origin marker to an error that is not about an origin", () => {
    const error = { response: { data: { errors: ["newSample.name: This field is too long"] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("This field is too long");
  });

  test("keeps an error that has no path prefix", () => {
    const error = { response: { data: { errors: ["Something went wrong"] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Something went wrong");
  });

  test("falls back to the message when the only error is blank", () => {
    // ApiError wraps its fourth constructor argument in a singleton list, so every response that is
    // not a BindException carries `errors: [""]`. That covers the 409, 404 and 403 cases this very
    // change cares about, and returning "" would show a titled alert with no message at all.
    const error = { response: { data: { message: "Edit conflict", errors: [""] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Edit conflict");
  });

  test("falls back when an error is nothing but a field path", () => {
    const error = { response: { data: { message: "Errors detected: 1", errors: ["origins[0].id:"] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Errors detected: 1");
  });

  test("does not strip a leading word that is not a field path", () => {
    const error = { response: { data: { errors: ["Warning: stock is low"] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Warning: stock is low");
  });

  test("falls back to the message when the errors array is empty", () => {
    const error = { response: { data: { message: "Errors detected: 0", errors: [] } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Errors detected: 0");
  });

  test("falls back to the message when there is no errors array", () => {
    const error = { response: { data: { message: "Plain failure" } } };
    expect(getApiErrorDetail(error, "fallback")).toBe("Plain failure");
  });

  test("falls back for a non-Axios error", () => {
    expect(getApiErrorDetail(new Error("boom"), "fallback")).toBe("boom");
    expect(getApiErrorDetail(null, "fallback")).toBe("fallback");
  });
});
