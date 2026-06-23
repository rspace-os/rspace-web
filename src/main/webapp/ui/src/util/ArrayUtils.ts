import { lift2, Optional } from "./optional";
import Result from "./result";

/**
 * Given two arrays of equal length, create a new array by applying a function
 * to elements at each index in turn.
 */
export const zipWith = <A, B, C>(
  listA: ReadonlyArray<A>,
  listB: ReadonlyArray<B>,
  f: (a: A, b: B, index: number) => C,
): Array<C> => {
  if (listA.length !== listB.length) {
    throw new Error("Both lists must have the same length");
  }
  return listA.map((a, i) => f(a, listB[i], i));
};

/**
 * Extract the head of the passed array, if there is one.
 */
export function head<T>(array: ReadonlyArray<T>): Result<T> {
  return array.length > 0 ? Result.Ok(array[0]) : Result.Error([new Error("Array is empty")]);
}

/**
 * Return a new array where between each pair of elements in the passed array
 * the constant value is inserted.
 *
 * @arg a  The constant value to be inserted between the elements.
 * @arg as The source array from which elements will be copied.
 */
export const intersperse = <A>(a: A, as: ReadonlyArray<A>): Array<A> => {
  if (as.length === 0) return [];
  const output = [as[0]];
  for (let i = 1; i < as.length; i++) {
    output.push(a);
    output.push(as[i]);
  }
  return output;
};

/**
 * Keeps the elements from the first array where the element of the same index
 * in the second array is true. Neither array should contain null as that will
 * break. Useful for property-based testing where values can be randonly chosen
 * from an array by generating random array of booleans of the same length.
 */
export const takeWhere = <A>(as: ReadonlyArray<A>, where: ReadonlyArray<boolean>): Array<A> => {
  if (as.length !== where.length) throw new Error("length must match");
  const output = [];
  for (let i = 0; i < as.length; i++) {
    if (where[i]) output.push(as[i]);
  }
  return output;
};

/**
 * Map over the passed array, keeping only those elements which when applied to
 * the passed function are wrapped in an Optional.present. This can be useful
 * when discarding particular values from an array. If the values you're
 * looking to discard are nulls then use `array.filter(isNotNil)` from
 * `es-toolkit` instead.
 */
export const mapOptional = <A, B>(f: (a: A) => Optional<B>, as: ReadonlyArray<A>): Array<B> => {
  const output: Array<B> = [];
  for (const a of as) {
    f(a).do((b) => output.push(b));
  }
  return output;
};

/**
 * Converts an array of optionals into an optional array by returning
 * Optional.present if all of elements of the input array are Optional.present.
 * Otherwise, Optional.empty is returned. This is similar to Promise.all (hence
 * the same name) where that function takes as input `Array<Promise<A>>` and
 * returns `Promise<Array<A>>`
 *
 * Usage:
 *   all([Optional.present(1), Optional.present(2)]) // Optional.present([1,2])
 *   all([Optional.present(1), Optional.empty()])    // Optional.empty()
 *   all([])                                         // Optional.present([])
 */
export const all = <A>(as: ReadonlyArray<Optional<A>>): Optional<Array<A>> => {
  if (as.length === 0) return Optional.present([]);
  const [h, ...t] = as;
  return lift2((newHead, newTail) => [newHead, ...newTail], h, all(t));
};
