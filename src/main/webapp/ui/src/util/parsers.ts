import { getByKey } from "./optional";
import Result from "./result";
import { match } from "./Util";

/*
 * These are some simple functions for converting strings into slightly more
 * structured types, providing for simpler code where Flow has to be convinced
 * of the correct type. All of the functions in this module should return a
 * Result, with the functions being composed together with the `flatMap`
 * method.
 */

/*
 * These first set of functions are true parsers: they take strings as inputs
 * and return their respective values wrapped in a Result.
 */

/**
 * Take some string, let's call it `value`. This function will parse it into a
 * type `T` which is a union of several strings, one of which is a specified
 * pattern. Here's an example:
 * ```
 * type Options = "Foo" | "Bar";
 * const value: string = "Foo";
 * const parsedValue: Options = parseString("Foo", value);
 * ```
 */
export function parseString<T extends string>(
  pattern: T,
  value: string
): Result<T> {
  if (pattern === value) return Result.Ok(pattern);
  return Result.Error<T>([new Error(`"${value}" !== "${pattern}"`)]);
}

/**
 * Parses a string into an integer, returning null if it doesn't
 * parse rather than NaN as parseInt normally does.
 */
export function parseInteger(value: string): Result<number> {
  return Number.isNaN(parseInt(value, 10))
    ? Result.Error([new Error(`"${value}" is not an integer`)])
    : Result.Ok(parseInt(value, 10));
}

/**
 * Parse a boolean from a string of either "true" or "false".
 */
export const parseBoolean: (value: "true" | "false") => Result<boolean> = match(
  [
    [(value: "false" | "true") => value === "false", Result.Ok(false)],
    [(value: "false" | "true") => value === "true", Result.Ok(true)],
    [() => true, Result.Error([new Error("Neither 'true' nor 'false'")])],
  ]
);

/**
 * Parses a string into a date.
 */
export function parseDate(s: string | number): Result<Date> {
  const d = new Date(s);
  if (d.toString() === "Invalid Date")
    return Result.Error([new Error(`"${s}" is not a valid date.`)]);
  return Result.Ok(d);
}

/*
 * These functions do not generate new values nor mutate their inputs but
 * simply convert the type of the passed value from a very broad class of
 * values to much more narrow set of possible values.
 *
 * Many of them take a value of type `mixed` as input which is any possible
 * value (number, string, object, etc.) and is more type-safe than the
 * deprecated `any` type. Still others take a type that has been somewhat
 * refined, and further refine it into an even more precise type. In most
 * cases, several of these functions should be composed together with Result's
 * `flatMap` method to refine a particular value from the broadest of possible
 * values (e.g. any value JSON value that could come from an API call) down to
 * a very specific type of value that an algorithm expects.
 */

/**
 * Parses anything into an Object (i.e. not a primitive value
 * like string or number).
 */
export function isObject(m: unknown): Result<object | null> {
  return typeof m === "object"
    ? Result.Ok(m)
    : Result.Error([new TypeError("Not an object")]);
}

/**
 * Parses something that might be null into the something that certainly is.
 */
export function isNull<T>(x: T | null): Result<null> {
  return x === null
    ? Result.Ok(null)
    : Result.Error<null>([new TypeError("Is not null")]);
}

/**
 * Parses something that might be null into just that something.
 *
 * Often composed with `isObject` to parse a `mixed` value into one that is
 * certain to be a set of key-value pairs as `null` is a type of object.
 * ```
 *   const isKeyValuePair = (m: mixed): Result<{ ... }> =>
 *     isObject(m).flatMap(isNotNull);
 * ```
 */
export function isNotNull<T>(x: T | null): Result<T> {
  return x === null
    ? Result.Error<T>([new TypeError("Is null")])
    : Result.Ok(x);
}

/**
 * Parsers something that might be bottom (undefined or null) into just that
 * something.
 */
export function isNotBottom<T>(x: T | undefined | null): Result<T> {
  return typeof x === "undefined" || x === null
    ? Result.Error<T>([new TypeError("Is undefined or null")])
    : Result.Ok(x);
}

/**
 * Parses anything into an array of anything
 */
export function isArray(m: unknown): Result<ReadonlyArray<unknown>> {
  return Array.isArray(m)
    ? Result.Ok(m)
    : Result.Error([new TypeError("Is not an array")]);
}

/**
 * Parses anything into a string
 */
export function isString(m: unknown): Result<string> {
  return typeof m === "string"
    ? Result.Ok(m)
    : Result.Error([new TypeError("Is not a string")]);
}

/**
 * Parses anything into a number
 */
export function isNumber(m: unknown): Result<number> {
  return typeof m === "number"
    ? Result.Ok(m)
    : Result.Error([new TypeError("Is not a number")]);
}

/**
 * Parsers a number into a number that doesn't included NaN.
 * Unfortunately, there's no way to encoded this in the type.
 */
export function isNotNaN(n: number): Result<number> {
  return Number.isNaN(n)
    ? Result.Error([new TypeError("In NaN")])
    : Result.Ok(n);
}

/**
 * Parses anything into a boolean
 */
export function isBoolean(m: unknown): Result<boolean> {
  return typeof m === "boolean"
    ? Result.Ok(m)
    : Result.Error([new TypeError("Is not a boolean")]);
}

/**
 * Parses booleans into true. I.e. If the boolean is true then Result.Ok is
 * returned, and Result.Error is if the boolean is false.
 */
export function isTrue(b: boolean): Result<true> {
  return b ? Result.Ok(b) : Result.Error([new TypeError("Is false")]);
}

/**
 * Given the key of an object, this function returns a new function that when
 * it is passed an object it attempts to parse out the value with that key. No
 * guarantees are made about the type of the value, so this should usually be
 * followed by a call to `.flatMap(typeFunc)` where `typeFunc` is something
 * like `isString`, `isNumber`, etc.
 * ```
 *   getValueWithKey("foo")({ foo: "bar" })   // Result.Ok("bar")
 *   getValueWithKey("foo")({})               // Result.Error([new Error("key 'foo' is missing")])
 *   const num: Result<number> = getValueWithKey("foo")({ foo: 4 }).flatMap(isNumber);
 * ```
 */
export const getValueWithKey =
  <Key extends string>(
    key: Key
  ): ((obj: Record<Key, unknown>) => Result<unknown>) =>
  (obj: Record<Key, unknown>): Result<unknown> =>
    getByKey(key, obj).toResult(() => new Error(`key '${key}' is missing`));
/**
 * Traverses a series of nested objects, only returning Result.Ok if each is an
 * object, is not null, and contains the specified key.
 * ```
 *   const num: Result<number> = objectPath(["foo", "bar"], {
 *     foo: { bar: 3 },
 *   }).flatMap(isNumber);     // Result.Ok(3)
 * ```
 */
export const objectPath = (
  path: ReadonlyArray<string>,
  obj: unknown
): Result<unknown> => {
  if (path.length === 0) return Result.Ok(obj);
  const [head, ...tail] = path;
  return isObject(obj)
    .flatMap(isNotNull)
    .flatMap((x) => getValueWithKey(head)(x as Record<string, unknown>))
    .flatMap((x) => objectPath(tail, x));
};
