// @flow strict

import { type AllSettled } from "./types";
import * as ArrayUtils from "./ArrayUtils";

/**
 * Bound a number between a minimum and maximum value.
 */
export const clamp = (num: number, min: number, max: number): number => {
  if (num > max) return max;
  if (num < min) return min;
  return num;
};

/**
 * Wrap an event handler function to prevent the event from bubbling up the
 * DOM.
 */
export const preventEventBubbling =
  (f?: (Event) => void = () => {}): ((Event) => void) =>
  (e: Event): void => {
    e.stopPropagation();
    return f(e);
  };

/**
 * Wrap an event handler function to prevent the default action of the event.
 */
export const preventEventDefault =
  (f?: (Event) => void = () => {}): ((Event) => void) =>
  (e: Event): void => {
    e.preventDefault();
    return f(e);
  };

/**
 * Remove all null, undefined, and empty string values from an object.
 * @deprecated
 */
export const omitNull = <T: { ... }>(obj: T): Partial<T> => {
  Object.keys(obj)
    .filter(
      (k) => obj[k] === null || obj[k] === "" || typeof obj[k] === "undefined"
    )
    .forEach((k) => delete obj[k]);
  return obj;
};

/**
 * Convert a string to title case, uppercasing the first letter of each word
 * and lowercasing the rest.
 */
export const toTitleCase = (str: string): string =>
  str.replace(
    /\w\S*/g,
    (word) => word.charAt(0).toUpperCase() + word.substr(1).toLowerCase()
  );

/**
 * Convert a string to sentence case, uppercasing the first letter of the
 * string.
 */
export const capitaliseJustFirstChar = (str: string): string =>
  str.replace(/^\w/, (char) => char.toUpperCase());

/**
 * Apply a function to each value in an object and return a new object with
 * the same keys.
 *
 * @arg f The function to apply to each value. The function will be called with
 *        the key and value.
 * @arg obj The object to map over.
 */
export const mapObject = <K, V, W>(
  f: (K, V) => W,
  obj: {| [K]: V |}
): {| [K]: W |} =>
  Object.keys(obj).reduce((acc, k) => ({ [k]: f(k, obj[k]), ...acc }), {});

/**
 * Apply a function to each key and value in an object and return a new object
 * with the new keys and values.
 *
 * @arg keyFunc   The function to apply to each key. The function will be
 *                called with the key and value.
 * @arg valueFunc The function to apply to each value. The function will be
 *                called with the key and value.
 * @arg obj       The object to map over.
 */
export const mapObjectKeyAndValue = <K1, V1, K2, V2>(
  keyFunc: (K1, V1) => K2,
  valueFunc: (K1, V1) => V2,
  obj: {| [K1]: V1 |}
): {| [K2]: V2 |} =>
  Object.keys(obj).reduce((acc, k) => {
    acc[keyFunc(k, obj[k])] = valueFunc(k, obj[k]);
    return acc;
  }, ({}: { [K2]: V2 }));

/**
 * Flow doesn't support Object.values so this function provides a wrapper
 * around the handy method that has the correct type.
 */
export const values = <K: string, V>(obj: { [K]: V }): Array<V> =>
  Object.values(obj);

/**
 * Filters an object in much the same way that Array.prototype.filter filters
 * arrays.
 *
 * @example
 *  filterObject(
 *    (key, value) => key !== "foo" && value > 3,
 *    { foo: 4, bar: 2, baz: 5 }
 *  )    // { baz: 5 }
 */
export const filterObject = <K: string, V>(
  f: (K, V) => boolean,
  obj: { [K]: V }
): { [K]: V } =>
  Object.fromEntries(
    Object.entries(obj).filter(([key, value]) => f(key, value))
  );

/**
 * Swaps the key for values and the values for keys.
 */
export const invertObject = <K: string, V>(obj: { [K]: V }): { [V]: K } => {
  return Object.fromEntries(Object.entries(obj).map(([k, v]) => [v, k]));
};

/**
 * Checks the equality of two objects, where we define equality to be the
 * same keys and the same (primitive) values
 */
export const sameKeysAndValues = (obj1: { ... }, obj2: { ... }): boolean =>
  ArrayUtils.zipWith(
    Object.entries(obj1),
    Object.entries(obj2),
    ([k1, v1], [k2, v2]) => k1 === k2 && v1 === v2
  ).every(Boolean);

/**
 * Basically the same as the `delete` keyword, but in an immutable way, that is
 * easier for flow to type check.
 */
export const dropProperty = <Key: string, Rest>(
  obj: { [Key]: mixed, ...Rest },
  key: Key
): Rest => {
  const copy = { ...obj };
  delete copy[key];
  return copy;
};

/**
 * Basic pattern matching
 * Takes a list of predicate functions and corresponding values
 * Returns the first value for which the predicate returns true
 * Throws an error if no pattern matches a given input
 * @example
 *   const matcher = match(
 *     [(x) => x === "foo", 7],
 *     [(x) => x === "bar", 8],
 *     [() => true,         9],
 *   ]);
 *   matcher("foo");        // 7
 *   matcher("bar");        // 8
 *   matcher(anythingElse); // 9
 */
export function match<T, U>(pairs: Array<[(T) => boolean, U]>): (T) => U {
  return function (inputs: T): U {
    for (const [predicate, output] of pairs) {
      if (predicate(inputs)) return output;
    }
    throw new Error("No pattern matches");
  };
}

/**
 * For formatting boolean values as Yes/No strings
 */
export const toYesNo = (b: boolean): string => (b ? "Yes" : "No");

type IsoToLocalOptions = {|
  locale?: ?string,
  dateOnly?: ?boolean,
|};
/**
 * Formats an ISO formatted date string according to the specified locale. If
 * not specified, the locale of the user's browser is used.
 */
export const isoToLocale = (
  isoString: string,
  { locale, dateOnly }: IsoToLocalOptions = {}
): string => {
  if (typeof locale === "string") {
    return new Date(Date.parse(isoString)).toLocaleString(locale, {
      ...(dateOnly ?? false
        ? {}
        : {
            hour: "numeric",
            minute: "numeric",
          }),
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  }
  return new Date(Date.parse(isoString)).toLocaleString();
};

/**
 * Creates an object by applying a function to each element of a list and using
 * the result as the value of the object.
 */
export const listToObject = <T, V>(list: Array<T>, f: (T) => V): { [T]: V } =>
  Object.fromEntries(list.map((x) => [x, f(x)]));

/**
 * Creates a set from an object by filtering out the keys that have a falsy
 * value.
 */
export const objectToSet = <K>(obj: { [K]: boolean }): Set<K> =>
  new Set(Object.keys(obj).filter((k) => obj[k]));

/**
 * Returns a promise the resolves after a given number of milliseconds.
 */
export const sleep = (milliseconds: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));

/**
 * Checks that an object has no keys.
 */
export const isEmptyObject = (obj: { ... }): boolean =>
  typeof obj === "object" && Boolean(obj) && Object.keys(obj).length === 0;

/**
 * Explicitly execute a function that returns a promise whilst ignoring its
 * return value. Useful when flow requires that event handlers return void.
 */
export function doNotAwait<T>(
  f: (...Array<T>) => Promise<mixed>
): (...Array<T>) => void {
  return function (...t: Array<T>): void {
    void f(...t);
  };
}

/**
 * Returns a new map with only the key-value pairs that satisfy the predicate.
 */
export const filterMap = <A, B>(
  map: Map<A, B>,
  f: (A, B) => boolean
): Map<A, B> => new Map([...map.entries()].filter(([k, v]) => f(k, v)));

/**
 * Combine two classes into one.
 * @deprecated
 */
export function classMixin<T>(cls: Class<T>, ...src: Array<T>): void {
  for (const _cl of src) {
    // $FlowFixMe[incompatible-use]
    for (const key of Object.getOwnPropertyNames(_cl.prototype)) {
      // $FlowFixMe[incompatible-use]
      cls.prototype[key] = _cl.prototype[key];
    }
  }
}

/**
 * AllSettled is the type returned by Promise.allSettled. This function
 * partitions the returned values and errors into two arrays.
 */
export const partitionAllSettled = <A>(
  allSettled: AllSettled<A>
): {|
  fulfilled: Array<A>,
  rejected: Array<Error>,
|} => {
  const partitioned = {
    fulfilled: ([]: Array<A>),
    rejected: ([]: Array<Error>),
  };
  for (const promise of allSettled) {
    if (promise.status === "fulfilled") {
      partitioned.fulfilled.push(promise.value);
    } else {
      partitioned.rejected.push(promise.reason);
    }
  }
  return partitioned;
};

/**
 * A promise wrapper around FileReader.readAsBinaryString
 */
export const readFileAsBinaryString = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    // $FlowExpectedError[incompatible-call] After calling readAsBinaryString reader.result will be a string
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsBinaryString(file);
  });
};

/**
 * Checks if a date is valid. Note that `parseDate` in ./parsers is probably
 * what you want instead as the type checker will refine the type of the passed
 * string.
 */
export const isValidDate = (str: string): boolean =>
  new Date(str).toString() !== "Invalid Date";

/**
 * Checks if a string is a valid URL.
 */
export const isUrl = (str: string): boolean => {
  try {
    new URL(str);
    return true;
  } catch {
    return false;
  }
};

/**
 * Checks if a string is a valid Inventory URL to a container, sample,
 * template, or subsample.
 */
export const isInventoryPermalink = (str: string): boolean => {
  return (
    isUrl(str) &&
    /\/inventory\/(container|sample|sampletemplate|subsample)\/\d+$/.test(str)
  );
};

/**
 * Applies a function to a value if it is not null or undefined.
 * Note that you probably want to use Optional (./optional.js) or Result
 * (./result.js) instead.
 */
export const mapNullable = <A, B>(f: (A) => B, a: ?A): ?B => {
  return a === null || typeof a === "undefined" ? a : f(a);
};

/**
 * Calculates the modulo of two notes. Note that this is distinct from the
 * remainder.
 */
export const modulo = (a: number, b: number): number => ((a % b) + b) % b;
