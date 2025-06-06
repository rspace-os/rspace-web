/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import Result from "../../result";
import "@testing-library/jest-dom";
import fc from "fast-check";

describe("toPromise", () => {
  test("When the Result is OK, the promise should resolve", () => {
    return fc.assert(
      fc.asyncProperty(fc.anything(), async (expectedValue) => {
        const actualValue = await Result.Ok(expectedValue).toPromise();
        expect(actualValue).toBe(expectedValue);
      })
    );
  });

  test("When there are multiple errors, they should be wrapped in an AggregateError", async () => {
    const errors = [new Error("foo"), new Error("bar")];
    try {
      await Result.Error<unknown>(errors).toPromise();
    } catch (e) {
      expect(e).toBeInstanceOf(AggregateError);
      expect((e as AggregateError).errors).toEqual(errors);
    }
  });

  test("When there is one error, it should simply be the rejected value", async () => {
    const errors = [new Error("foo")];
    try {
      await Result.Error<unknown>(errors).toPromise();
    } catch (e) {
      expect(e).not.toBeInstanceOf(AggregateError);
      expect(e).toEqual(errors[0]);
    }
  });
});
