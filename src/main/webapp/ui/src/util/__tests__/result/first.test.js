//@flow
/* eslint-env jest */
import Result from "../../result";

describe("first", () => {
  test("A single OK, should be the value.", () => {
    const actual = Result.first(Result.Ok("foo"));
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual("foo");
    });
  });
  test("A single Error, should return itself.", () => {
    const actual = Result.first(Result.Error<mixed>([new Error("foo")]));
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["foo"]);
    });
  });
  test("Multiple OK, should return first value.", () => {
    const actual = Result.first(Result.Ok("foo"), Result.Ok("bar"));
    expect(actual.isOk).toBe(true);
    actual.do((values) => {
      expect(values).toEqual("foo");
    });
  });
  test("Multiple Errors, should return Error.", () => {
    const actual = Result.first(
      Result.Error<mixed>([new Error("foo")]),
      Result.Error<mixed>([new Error("bar")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar", "foo"]);
    });
  });
  test("Mix of OK and Error, should return first OK value.", () => {
    const actual = Result.first(
      Result.Ok<string>("foo"),
      Result.Error<string>([new Error("bar")]),
      Result.Ok<string>("bar")
    );
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual("foo");
    });
  });
});
