/* eslint-env jest */
import Result from "../../result";

describe("any", () => {
  test("A single OK, should be wrapped in an array.", () => {
    const actual = Result.any(Result.Ok("foo"));
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual(["foo"]);
    });
  });
  test("A single Error, should return itself.", () => {
    const actual = Result.any(Result.Error<unknown>([new Error("foo")]));
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["foo"]);
    });
  });
  test("Multiple OK, should return all values.", () => {
    const actual = Result.any(Result.Ok("foo"), Result.Ok("bar"));
    expect(actual.isOk).toBe(true);
    actual.do((values) => {
      expect(values).toEqual(["foo", "bar"]);
    });
  });
  test("Multiple Errors, should return Error.", () => {
    const actual = Result.any(
      Result.Error<unknown>([new Error("foo")]),
      Result.Error<unknown>([new Error("bar")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar", "foo"]);
    });
  });
  test("Mix of OK and Error, should return OK value.", () => {
    const actual = Result.any(
      Result.Ok<string>("foo"),
      Result.Error<string>([new Error("bar")])
    );
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual(["foo"]);
    });
  });
});
