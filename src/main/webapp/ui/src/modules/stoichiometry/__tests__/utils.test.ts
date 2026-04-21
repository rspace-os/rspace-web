import { describe, expect, it } from "vitest";
import { toStoichiometryError } from "@/modules/stoichiometry/utils";

describe("toStoichiometryError", () => {
  it("returns REST API error message when payload matches RestApiError schema", () => {
    const error = toStoichiometryError(
      {
        status: "Bad Request",
        httpCode: 400,
        internalCode: 1000,
        message: "REST API error",
        messageCode: null,
        errors: [],
        iso8601Timestamp: "2026-01-01T12:00:00Z",
        data: null,
      },
      "fallback message",
    );

    expect(error.message).toBe("REST API error");
  });

  it("returns message error response when payload has message shape", () => {
    const error = toStoichiometryError(
      {
        message: "Stoichiometry error message",
      },
      "fallback message",
    );

    expect(error.message).toBe("Stoichiometry error message");
  });

  it("returns fallback message when payload is unknown", () => {
    const error = toStoichiometryError({ nope: true }, "fallback message");

    expect(error.message).toBe("fallback message");
  });
});
