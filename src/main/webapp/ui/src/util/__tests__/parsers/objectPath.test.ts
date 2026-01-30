/*
 */
import { describe, expect, it } from "vitest";
import { objectPath } from "../../parsers";
import Result from "../../result";
import "@testing-library/jest-dom/vitest";

describe("objectPath", () => {
  it("Recursive example", () => {
    objectPath(["foo", "bar"], { foo: { bar: 3 } }).do((value) =>
      expect(value).toBe(3)
    );
  });
  it("Error case from missing property", () => {
    objectPath(["foo", "bar"], { foo: {} })
      .orElseTry(([error]) => {
        expect(error.message).toBe("key 'bar' is missing");
        return Result.Error<unknown>([error]);
      })
      .do(() => {
        throw new Error("Should fail");
      });
  });
  it("Error case from null", () => {
    objectPath(["foo", "bar"], { foo: null })
      .orElseTry(([error]) => {
        expect(error.message).toBe("Is null");
        return Result.Error<unknown>([error]);
      })
      .do(() => {
        throw new Error("Should fail");
      });
  });
  it("Error case from not being an object", () => {
    objectPath(["foo", "bar"], { foo: 3 })
      .orElseTry(([error]) => {
        expect(error.message).toBe("Not an object");
        return Result.Error<unknown>([error]);
      })
      .do(() => {
        throw new Error("Should fail");
      });
  });
});


