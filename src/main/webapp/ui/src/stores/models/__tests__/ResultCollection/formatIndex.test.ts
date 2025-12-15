/* eslint-env jest */
import fc from "fast-check";
import { formatIndex } from "../../InventoryBaseRecordCollection";
import { take, incrementForever } from "../../../../util/iterators";
import RsSet from "../../../../util/set";

jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

// even a max of 1000 is probably overkill
const arbitraryInput = fc.nat(1000);

const arbitraryInputs = fc
  .tuple(arbitraryInput, arbitraryInput)
  .map(([a, b]) => (a < b ? [a, b + 1] : [b, a + 1]))
  .filter(([a, b]) => a !== b);

const everyOutputForArbitraryNumOfRecords = arbitraryInput
  .map((n) => n + 1)
  .map((numOfRecords) =>
    new RsSet(take(incrementForever(), numOfRecords)).map((i) =>
      formatIndex(i, numOfRecords)
    )
  );

/*
 * This test suite provides a complete specification for the helper function,
 * formatIndex. The function's job is to convert a given number to a string,
 * padding sufficient zeros to the front to ensure that all indexes within a
 * given range end up with the same length string.
 *
 * These four property tests are sufficient constraints on the implementation
 * of the function to ensure that it must be correct. The only way for a
 * string, containing only digits, to have the required length is for it to be
 * padded with zeros. The parsing test then ensures that the particular value
 * encoded in the string is correct. Finally, the leading zero test asserts
 * that the number of leading zeros is the minimal number necessary.
 */
describe("formatIndex", () => {
  test("Output should only contain digits.", () => {
    fc.assert(
      fc.property(arbitraryInputs, ([index, numOfRecords]) => {
        expect(/^\d+$/.test(formatIndex(index, numOfRecords))).toBe(true);
      })
    );
  });
  test("For a given numOfRecord, all outputs should have the same length.", () => {
    fc.assert(
      fc.property(everyOutputForArbitraryNumOfRecords, (outputs) => {
        expect(outputs.map((s) => s.length).size).toBe(1);
      })
    );
  });
  test("Parsed output should give index input plus one.", () => {
    fc.assert(
      fc.property(arbitraryInputs, ([index, numOfRecords]) => {
        expect(parseInt(formatIndex(index, numOfRecords), 10)).toEqual(
          index + 1
        );
      })
    );
  });
  test("The number of leading zeros should be minimal.", () => {
    fc.assert(
      fc.property(everyOutputForArbitraryNumOfRecords, (outputs) => {
        expect(outputs.some((x) => x[0] !== "0")).toBe(true);
      })
    );
  });
});
