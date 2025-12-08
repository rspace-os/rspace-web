/**
 * This script contains a number of utility functions for working with
 * iterators and generators. Here are a few definitions used in this script:
 *   - Iterator           - An object that implements the iterator protocol,
 *                          which consists of a next() method that returns an
 *                          object with two properties: done and value.
 *   - Iterable           - An object that implements the iterable protocol,
 *                          by having a Symbol.iterator method that returns an
 *                          iterator.
 *   - Generator Function - Differentiated from regular functions by the
 *                          asterisk after the function keyword, when called
 *                          they do not initially execute, instead returning a
 *                          Generator that starts executing on the first `next`
 *                          call.
 *   - Generator          - An object returned by a generator function, which
 *                          conforms to both the iterable and iterator protocols.
 *   - yield              - A keyword that is used to pause and resume a
 *                          generator function. It returns the arguments passed
 *                          to the next() method.
 * For more information, see the MDN documentation on iterators and generators.
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Iterators_and_generators
 */

/**
 * Creates a generator that produces an infinite sequence of numbers starting
 * at 0. Useful for indexing over other iterators.
 */
export function* incrementForever(): Generator<number, void, void> {
    for (let i = 0; ; i++) yield i;
}

/**
 * Returns a generator that re-produces a finite prefix of the passed iterator.
 *
 * @arg iterator - The iterator to take from.
 * @arg n        - The number of elements to take.
 */
export function* take<T>(iterator: Iterable<T>, n: number): Generator<T, void, void> {
    let count = n;
    for (const x of iterator) {
        if (count === 0) return;
        yield x;
        count--;
    }
}

/**
 * Compute the sum of an iterator of numbers.
 */
export function sum(iterator: Iterable<number>): number {
    let result = 0;
    for (const x of iterator) result += x;
    return result;
}
