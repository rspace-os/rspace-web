import { describe, expect, it, vi } from "vitest";
import {
  resolveToken,
  toStoichiometryError,
} from "@/modules/stoichiometry/utils";

describe("resolveToken", () => {
  it("returns direct token when provided and does not call getToken", async () => {
    const getToken = vi.fn(async () => "callback-token");

    const result = await resolveToken({ token: "direct-token", getToken });

    expect(result).toBe("direct-token");
    expect(getToken).not.toHaveBeenCalled();
  });

  it("uses getToken when direct token is not provided", async () => {
    const getToken = vi.fn(async () => "callback-token");

    const result = await resolveToken({ getToken });

    expect(result).toBe("callback-token");
    expect(getToken).toHaveBeenCalledTimes(1);
  });

  it("throws when neither token nor getToken is provided", async () => {
    await expect(resolveToken({})).rejects.toThrow(
      "Token is required to perform this operation",
    );
  });
});

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
