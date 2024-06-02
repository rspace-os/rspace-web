/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import fc from "fast-check";
import * as ArrayUtils from "../../ArrayUtils";

describe("partition", () => {
  test("If the predicate always returns true, then the second array is empty.", () => {
    fc.assert(
      fc.property(fc.array(fc.anything()), (list) => {
        const [, no] = ArrayUtils.partition(() => true, list);
        expect(no.length).toBe(0);
      })
    );
  });

  test("If the predicate always returns false, then the first array is empty.", () => {
    fc.assert(
      fc.property(fc.array(fc.anything()), (list) => {
        const [yes] = ArrayUtils.partition(() => false, list);
        expect(yes.length).toBe(0);
      })
    );
  });

  test("The size of the two resulting arrays will always sum to the size of the input array.", () => {
    fc.assert(
      fc.property(
        fc.func<mixed, boolean>(fc.boolean()),
        fc.array(fc.anything()),
        (predicate, list) => {
          const [yes, no] = ArrayUtils.partition(predicate, list);
          expect(yes.length + no.length).toBe(list.length);
        }
      )
    );
  });

  test("Partition distributes over concatenation.", () => {
    fc.assert(
      fc.property(
        fc.func<mixed, boolean>(fc.boolean()),
        fc.array(fc.anything()),
        fc.array(fc.anything()),
        (predicate, listA, listB) => {
          const [yes, no] = ArrayUtils.partition(predicate, [
            ...listA,
            ...listB,
          ]);
          const [listAyes, listAno] = ArrayUtils.partition(predicate, listA);
          const [listByes, listBno] = ArrayUtils.partition(predicate, listB);
          expect(yes).toEqual([...listAyes, ...listByes]);
          expect(no).toEqual([...listAno, ...listBno]);
        }
      )
    );
  });

  test("Order is maintained.", () => {
    fc.assert(
      fc.property(
        fc.func<mixed, boolean>(fc.boolean()),
        fc.array(fc.string()),
        (predicate, list) => {
          const sorted = list.sort();
          const [yes, no] = ArrayUtils.partition(predicate, sorted);
          const sortedYes = yes.sort();
          expect(sortedYes).toEqual(yes);
          const sortedNo = no.sort();
          expect(sortedNo).toEqual(no);
        }
      )
    );
  });
});
