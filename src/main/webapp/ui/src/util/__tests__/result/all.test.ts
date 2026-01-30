import { describe, expect, it } from "vitest";
import Result from "../../result";

describe("all", () => {
  it("A single OK, should be wrapped in an array.", () => {
    const actual = Result.all(Result.Ok("foo"));
    expect(actual.isOk).toBe(true);
    actual.do((value) => {
      expect(value).toEqual(["foo"]);
    });
  });
  it("A single Error, should return itself.", () => {
    const actual = Result.all(Result.Error<unknown>([new Error("foo")]));
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["foo"]);
    });
  });
  it("Multiple OK, should return all values.", () => {
    const actual = Result.all(Result.Ok("foo"), Result.Ok("bar"));
    expect(actual.isOk).toBe(true);
    actual.do((values) => {
      expect(values).toEqual(["foo", "bar"]);
    });
  });
  it("Multiple Errors, should return Error.", () => {
    const actual = Result.all(
      Result.Error<unknown>([new Error("foo")]),
      Result.Error<unknown>([new Error("bar")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar", "foo"]);
    });
  });
  it("Mix of OK and Error, should return Error value.", () => {
    const actual = Result.all(
      Result.Ok<string>("foo"),
      Result.Error<string>([new Error("bar")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar"]);
    });
  });
  it("Mix of OK, Error, and Error, should return Error values.", () => {
    const actual = Result.all(
      Result.Ok<string>("foo"),
      Result.Error<string>([new Error("error1")]),
      Result.Error<string>([new Error("error2")])
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["error2", "error1"]);
    });
  });
  it("Mix of Error and OK, should return Error value.", () => {
    const actual = Result.all(
      Result.Error<string>([new Error("bar")]),
      Result.Ok<string>("foo")
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["bar"]);
    });
  });
  it("Mix of Error, Error, and OK, should return Error values.", () => {
    const actual = Result.all(
      Result.Error<string>([new Error("error1")]),
      Result.Error<string>([new Error("error2")]),
      Result.Ok<string>("foo")
    );
    expect(actual.isError).toBe(true);
    actual.orElseGet((errors) => {
      expect(errors.map((e) => e.message)).toEqual(["error2", "error1"]);
    });
  });
  it("Empty array in, empty array out", () => {
    const input: ReadonlyArray<Result<unknown>> = [];
    const actual = Result.all(...input);
    expect(actual.isOk).toBe(true);
    actual.do((output) => {
      expect(output.length).toBe(0);
    });
  });
});


