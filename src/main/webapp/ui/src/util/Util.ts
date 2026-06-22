import type { AllSettled } from "./types";

/**
 * Wrap an event handler function to prevent the event from bubbling up the
 * DOM.
 */
export const preventEventBubbling =
  <E extends { stopPropagation: () => void }>(f: (e: E) => void = () => {}): ((e: E) => void) =>
  (e: E): void => {
    e.stopPropagation();
    // biome-ignore lint/correctness/noVoidTypeReturn: initial biome migration
    return f(e);
  };

/**
 * Convert a string to title case, uppercasing the first letter of each word
 * and lowercasing the rest.
 */
export const toTitleCase = (str: string): string =>
  str.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());

/**
 * Convert a string to sentence case, uppercasing the first letter of the
 * string.
 */
export const capitaliseJustFirstChar = (str: string): string => str.replace(/^\w/, (char) => char.toUpperCase());

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
export function match<T, U>(pairs: Array<[(t: T) => boolean, U]>): (t: T) => U {
  return (inputs: T): U => {
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

type IsoToLocalOptions = {
  locale?: string | undefined | null;
  dateOnly?: boolean | undefined | null;
};
/**
 * Formats an ISO formatted date string according to the specified locale. If
 * not specified, the locale of the user's browser is used.
 */
export const isoToLocale = (isoString: string, { locale, dateOnly }: IsoToLocalOptions = {}): string => {
  if (typeof locale === "string") {
    return new Date(Date.parse(isoString)).toLocaleString(locale, {
      ...((dateOnly ?? false)
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
 * Returns a new map with only the key-value pairs that satisfy the predicate.
 */
export const filterMap = <A, B>(map: Map<A, B>, f: (a: A, b: B) => boolean): Map<A, B> =>
  new Map([...map.entries()].filter(([k, v]) => f(k, v)));

/**
 * AllSettled is the type returned by Promise.allSettled. This function
 * partitions the returned values and errors into two arrays.
 */
export const partitionAllSettled = <A>(
  allSettled: AllSettled<A>,
): {
  fulfilled: Array<A>;
  rejected: Array<Error>;
} => {
  const partitioned: {
    fulfilled: Array<A>;
    rejected: Array<Error>;
  } = {
    fulfilled: [],
    rejected: [],
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
 * Checks if a date is valid. Note that `parseDate` in ./parsers is probably
 * what you want instead as the type checker will refine the type of the passed
 * string.
 */
export const isValidDate = (str: string): boolean => new Date(str).toString() !== "Invalid Date";

/**
 * Checks if a string is a valid Inventory URL to a container, sample,
 * template, or subsample.
 */
export const isInventoryPermalink = (str: string): boolean => {
  return URL.canParse(str) && /\/inventory\/(container|sample|sampletemplate|subsample)\/\d+$/.test(str);
};

/**
 * Applies a function to a value if it is not null or undefined.
 * Note that you probably want to use Optional (./optional.ts) or Result
 * (./result.ts) instead.
 */
export const mapNullable = <A, B>(f: (a: A) => B, a: A | null | undefined): B | null | undefined => {
  if (a === null) return null;
  if (typeof a !== "undefined") return f(a);
};

/**
 * Calculates the modulo of two notes. Note that this is distinct from the
 * remainder.
 */
export const modulo = (a: number, b: number): number => ((a % b) + b) % b;
