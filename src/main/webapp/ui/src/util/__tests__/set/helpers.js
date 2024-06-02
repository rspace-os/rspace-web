//@flow

import fc, { type Arbitrary } from "fast-check";
import RsSet from "../../set";

export function arbRsSet<T>(
  arb: Arbitrary<T>,
  options: ?{|
    minSize?: number,
    maxSize?: number,
  |}
): Arbitrary<RsSet<T>> {
  const { minSize, maxSize } = options ?? {};
  const uniqueArrayOptions: {| minLength?: number, maxLength?: number |} = {};
  if (typeof minSize !== "undefined") uniqueArrayOptions.minLength = minSize;
  if (typeof maxSize !== "undefined") uniqueArrayOptions.maxLength = maxSize;
  return fc
    .uniqueArray(arb, uniqueArrayOptions)
    .map((array) => new RsSet(array));
}

export function arbSubsetOf<T>(arbSet: RsSet<T>): Arbitrary<RsSet<T>> {
  return fc.subarray(arbSet.toArray()).map((subarray) => new RsSet(subarray));
}

/*
 * This is used for testing subtractMap, intersectionMap, and unionWith
 */
export type ArbMappedFunctionArgs<A, B> = [RsSet<A>, RsSet<B>, (A) => B];
export const arbMappedFunctionArgs: Arbitrary<
  ArbMappedFunctionArgs<{ id: mixed }, mixed>
> = fc.uniqueArray(fc.anything()).chain((ids) => {
  const mapFn = ({ id }: { id: mixed }) => id;
  return fc.tuple<RsSet<{ id: mixed }>, RsSet<mixed>, typeof mapFn>(
    fc
      .shuffledSubarray(ids)
      .map((someIds) => new RsSet(someIds).map((id) => ({ id }))),
    fc.shuffledSubarray(ids).map((someIds) => new RsSet(someIds)),
    fc.constant(mapFn)
  );
});

/*
 * Functions like subtractMap, intersectionMap, and unionWith operate over a
 * collection of sets that are related based on some mapping function. This
 * arbitrary supports testing these functions by providing such a function and
 * several sets of arbitrary contents.
 */

export type ArbitraryMappableSets<A, B: { id: A }> = [
  (B) => A,
  RsSet<B>,
  RsSet<B>,
  RsSet<B>
];

export const arbitraryMappableSets: Arbitrary<
  ArbitraryMappableSets<mixed, { id: mixed }>
> = fc.uniqueArray(fc.anything()).chain((ids) => {
  function makeSetOfObjectsWithId() {
    return fc
      .shuffledSubarray(ids)
      .map((someIds) => new RsSet(someIds).map((id) => ({ id })));
  }
  return fc.tuple<
    ({ id: mixed }) => mixed,
    RsSet<{ id: mixed }>,
    RsSet<{ id: mixed }>,
    RsSet<{ id: mixed }>
  >(
    fc.constant(({ id }) => id),
    makeSetOfObjectsWithId(),
    makeSetOfObjectsWithId(),
    makeSetOfObjectsWithId()
  );
});

/*
 * This is for testing flattenWithUnion and flattenWithIntersection.
 *
 * This Arbitrary generates a set of between 1 and 3 sets, each containing
 * between 1 and 5 elements, taken from a pool of 5 random pieces of data.
 * This ensures that there will be a high degree of overlap between the sets and
 * that no set will be empty. This useful because intersection does not produce
 * interesting results if there is no overlap amongst the inputs.
 */
export const arbSetOfSetsWithHighOverlap: Arbitrary<RsSet<RsSet<mixed>>> = fc
  .uniqueArray(fc.anything(), { minLength: 5, maxLength: 5 })
  .chain((list) =>
    arbRsSet(
      fc.shuffledSubarray(list, { minLength: 1 }).map((x) => new RsSet(x)),
      { maxSize: 3, minSize: 1 }
    )
  );
