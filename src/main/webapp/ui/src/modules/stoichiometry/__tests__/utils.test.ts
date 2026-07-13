import { describe, expect, it } from "vitest";
import {
  resolveStoichiometryErrorMessage,
  StoichiometryFallbackError,
  toStoichiometryError,
} from "@/modules/stoichiometry/utils";

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
      "stoichiometry.errors.fetchFailed",
    );

    expect(error.message).toBe("REST API error");
  });

  it("returns message error response when payload has message shape", () => {
    const error = toStoichiometryError(
      {
        message: "Stoichiometry error message",
      },
      "stoichiometry.errors.fetchFailed",
    );

    expect(error.message).toBe("Stoichiometry error message");
  });

  it("returns a StoichiometryFallbackError carrying the pure key and values when payload is unknown", () => {
    const error = toStoichiometryError({ nope: true }, "stoichiometry.errors.fetchFailed", { status: "Not Found" });

    expect(error).toBeInstanceOf(StoichiometryFallbackError);
    expect((error as StoichiometryFallbackError).key).toBe("stoichiometry.errors.fetchFailed");
    expect((error as StoichiometryFallbackError).values).toEqual({ status: "Not Found" });
  });
});

describe("resolveStoichiometryErrorMessage", () => {
  const t = ((key: string, values?: Record<string, unknown>) =>
    values ? `${key}:${JSON.stringify(values)}` : key) as unknown as Parameters<
    typeof resolveStoichiometryErrorMessage
  >[1];

  it("translates the key for a StoichiometryFallbackError", () => {
    const error = new StoichiometryFallbackError("stoichiometry.errors.fetchFailed", { status: "Not Found" });

    expect(resolveStoichiometryErrorMessage(error, t, "fallback")).toBe(
      'stoichiometry.errors.fetchFailed:{"status":"Not Found"}',
    );
  });

  it("returns the message as-is for a plain Error", () => {
    const error = new Error("REST API error");

    expect(resolveStoichiometryErrorMessage(error, t, "fallback")).toBe("REST API error");
  });

  it("returns the fallback for a non-Error value", () => {
    expect(resolveStoichiometryErrorMessage("not an error", t, "fallback")).toBe("fallback");
  });
});
