/* eslint-env jest */
import { calculateProgress, asPercentageString } from "../progress";
import fc from "fast-check";

describe("progress", () => {
  test("When progress made is 0, the percentage string should be 0%.", () => {
    fc.assert(
      fc.property(fc.nat(), (total) => {
        fc.pre(total > 0);
        const progress = calculateProgress({ progressMade: 0, total });
        expect(asPercentageString(progress)).toEqual("0%");
      })
    );
  });

  test("When progress made is the same as the total, the percentage string should be 100%.", () => {
    fc.assert(
      fc.property(fc.nat(), (total) => {
        fc.pre(total > 0);
        const progress = calculateProgress({
          progressMade: total,
          total,
        });
        expect(asPercentageString(progress)).toEqual("100%");
      })
    );
  });

  test("When progress made is half the total, the percentage string should be 50%.", () => {
    fc.assert(
      fc.property(fc.nat(), (progressMade) => {
        fc.pre(progressMade > 0);
        const progress = calculateProgress({
          progressMade,
          total: progressMade * 2,
        });
        expect(asPercentageString(progress)).toEqual("50%");
      })
    );
  });
});
