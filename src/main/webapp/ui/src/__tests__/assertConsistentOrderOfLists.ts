/* eslint-env jest */

type Assertion = {
  pass: boolean;
  message: (() => string) | undefined;
};

type ListName = string;

type OrderedPairOfElementsInList = {
  list: Array<string>; // the array from which the pair came
  name: ListName;
  index1: number; // index of the first element in the pair
  index2: number; // index of the second element in the pair
};

const formatListOfOutput = ({
  list,
  index1,
  index2,
}: OrderedPairOfElementsInList): string => {
  const [indexOfA, indexOfB] = [index1, index2];
  const a = list[indexOfA];
  const b = list[indexOfB];
  const neitherIsFirstInList = indexOfA !== 0 && indexOfB !== 0;
  const neitherIsLastInList =
    indexOfA !== list.length - 1 && indexOfB !== list.length - 1;
  const areNotConsecutive = Math.abs(indexOfA - indexOfB) > 1;

  return [
    ...(neitherIsFirstInList ? ["..."] : []),
    indexOfA < indexOfB ? a : b,
    ...(areNotConsecutive ? ["..."] : []),
    indexOfA < indexOfB ? b : a,
    ...(neitherIsLastInList ? ["..."] : []),
  ].join(", ");
};

const formatErrorMessage = (
  expectLists: Array<OrderedPairOfElementsInList>,
  receivedList: OrderedPairOfElementsInList,
  printExpected: (val: string) => string,
  printReceived: (val: string) => string
) =>
  [
    `Expected:`,
    ...expectLists.map((expectList) =>
      [
        `  In ${expectList.name}:`,
        `    ${printExpected(formatListOfOutput(expectList))
          .replace('"', "[")
          .replace('"', "]")}`,
      ].join("\n")
    ),
    ``,
    `Received:`,
    `  In ${receivedList.name}:`,
    `   ${printReceived(formatListOfOutput(receivedList))
      .replace('"', "[")
      .replace('"', "]")}`,
  ].join("\n");

/*
 * Note: in each of the arrays, any given string MUST only occur once
 *
 * If each array is considered to be a path through a directed acyclical graph
 * (DAG), where each unique string is a node in such a DAG, then this algorithm
 * can be considered as isomorphic to a check for whether there exists a
 * topological sorting.
 */
export function toHaveConsistentOrdering(
  this: jest.MatcherUtils & Readonly<jest.MatcherState>,
  mapOfListsOfNumbers: Map<ListName, Array<string>>
): jest.CustomMatcherResult {
  /*
   * This Map map pairs of strings (x,y) to the list in which they are found
   * in a given order.
   *
   * So given a list A: ["foo", "bar", "baz"], {("foo", "bar"), ("foo", "baz"),
   * ("bar", "baz") will all map to A.
   */
  const seenPairs = new Map<string, Array<OrderedPairOfElementsInList>>();

  for (const [name, list] of mapOfListsOfNumbers) {
    for (let i = 0; i < list.length; i++) {
      const first = list[i];
      for (let j = i + 1; j < list.length; j++) {
        const second = list[j];
        /*
         * For each list, we take every pair of elements and assert that we
         * have not previously seen the same pair in the reverse order
         */
        const seen = seenPairs.get(JSON.stringify([second, first]));
        if (seen) {
          return {
            pass: false,
            message: () =>
              formatErrorMessage(
                seen,
                { name, list, index1: i, index2: j },
                this.utils.printExpected,
                this.utils.printReceived
              ),
          };
        }
        seenPairs.set(JSON.stringify([first, second]), [
          { list, name, index1: i, index2: j },
        ]);
        /*
         * In addition to the pair of elements currently be asserted, we also
         * add to seenPairs all of the transitive possibilities so that if we
         * have seen [a,b] and [b,c] then seeing [c,a] should now be an error.
         */
        for (const [json, lists] of seenPairs) {
          const [x, y] = JSON.parse(json);
          if (y === first)
            seenPairs.set(JSON.stringify([x, second]), [
              ...lists,
              { list, name, index1: i, index2: j },
            ]);
          if (x === second)
            seenPairs.set(JSON.stringify([first, y]), [
              { list, name, index1: i, index2: j },
              ...lists,
            ]);
        }
      }
    }
  }

  // no message is returned here because the assertion is never called before a .not
  return {
    pass: true,
    message: () => "",
  };
}

declare global {
  namespace jest {
    interface Matchers<R> {
      toHaveConsistentOrdering(): R;
    }
  }
}

expect.extend({
  toHaveConsistentOrdering,
});

export function assertConsistentOrderOfLists(
  lists: Map<ListName, Array<string>>
): void {
  expect(lists).toHaveConsistentOrdering();
}
