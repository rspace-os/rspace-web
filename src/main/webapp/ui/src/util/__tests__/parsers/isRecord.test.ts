/*
 * @vitest-environment jsdom
 */
import { describe, test, expect } from "vitest";
import { isRecord } from "../../parsers";
import "@testing-library/jest-dom/vitest";

describe("isRecord", () => {
  test("should pass for an object literal", () => {
    expect(isRecord({}).isOk).toBe(true);
  });

  test("should pass for an object constructor call", () => {
    expect(isRecord(new Object()).isOk).toBe(true);
  });

  test("should fail for an array", () => {
    expect(isRecord([]).isOk).toBe(false);
  });

  test("should fail for a set", () => {
    expect(isRecord(new Set()).isOk).toBe(false);
  });
});


