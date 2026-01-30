import { describe, expect, it } from "vitest";
import Result from "../../result";

describe("first", () => {
  it("A single OK, should be the value.", () => {
    const actual = Result.first(Result.Ok("foo"));
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual("foo");
    });
  });
  it("A single Error, should return itself.", () => {
    const actual = Result.first(Result.Error<unknown>([new Error("foo")]));
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["foo"]);
    });
  });
  it("Multiple OK, should return first value.", () => {
    const actual = Result.first(Result.Ok("foo"), Result.Ok("bar"));
    expect(actual.isOk).toBe(true);
    actual.do((values) => {
      expect(values).toEqual("foo");
    });
  });
  it("Multiple Errors, should return Error.", () => {
    const actual = Result.first(
      Result.Error<unknown>([new Error("foo")]),
      Result.Error<unknown>([new Error("bar")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar", "foo"]);
    });
  });
  it("Mix of OK and Error, should return first OK value.", () => {
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


