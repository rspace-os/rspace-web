/*
 */
import { describe, test, expect, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockSubSample } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  }}));

describe("isFieldEditable", () => {
  describe("When the Subsample is deleted, isFieldEditable should", () => {
    test("return false for notes.", () => {
      const subsample = makeMockSubSample({
        deleted: true,
      });

      expect(subsample.isFieldEditable("notes")).toBe(false);
    });
  });
});


