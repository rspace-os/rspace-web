import { test, describe, expect, vi } from 'vitest';
import fc from "fast-check";
import { formatIndex } from "../../InventoryBaseRecordCollection";
import { take, incrementForever } from "../../../../util/iterators";
import RsSet from "../../../../util/set";

vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({})
}));

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


