/*
 */
import { describe, expect, it } from "vitest";
import { isRecord } from "../../parsers";
import "@testing-library/jest-dom/vitest";

describe("isRecord", () => {
  it("should pass for an object literal", () => {
    expect(isRecord({}).isOk).toBe(true);
  });

  it("should pass for an object constructor call", () => {
    expect(isRecord(new Object()).isOk).toBe(true);
  });

  it("should fail for an array", () => {
    expect(isRecord([]).isOk).toBe(false);
  });

  it("should fail for a set", () => {
    expect(isRecord(new Set()).isOk).toBe(false);
  });
});


