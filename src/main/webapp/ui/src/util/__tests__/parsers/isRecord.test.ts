import { describe, expect, test } from 'vitest';
import { isRecord } from "../../parsers";
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

