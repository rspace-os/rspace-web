/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { getErrorMessage } from "../error";

describe("getErrorMessage", () => {
  test("should extract message from axios response data", () => {
    const error = {
      response: {
        data: {
          message: "Network error occurred"
        }
      }
    };

    expect(getErrorMessage(error, "fallback")).toBe("Network error occurred");
  });

  test("should extract exceptionMessage from axios response data", () => {
    const error = {
      response: {
        data: {
          exceptionMessage: "Something went wrong: \ndescription too long, should be max 250 chars\n\n"
        }
      }
    };

    expect(getErrorMessage(error, "fallback")).toBe("Something went wrong: \ndescription too long, should be max 250 chars\n\n");
  });

  test("should prefer message over exceptionMessage when both exist", () => {
    const error = {
      response: {
        data: {
          message: "Primary error message",
          exceptionMessage: "Secondary exception message"
        }
      }
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
          message: 123
        }
      }
    };

    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });

  test("should handle axios response with non-string exceptionMessage", () => {
    const error = {
      response: {
        data: {
          exceptionMessage: { nested: "object" }
        }
      }
    };

    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });

  test("should handle incomplete axios response structure", () => {
    const error = {
      response: {
        status: 500
      }
    };

    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });

  test("should handle object that looks like Error but isn't", () => {
    const error = {
      message: "fake error message"
    };

    expect(getErrorMessage(error, "fallback")).toBe("fallback");
  });
});
