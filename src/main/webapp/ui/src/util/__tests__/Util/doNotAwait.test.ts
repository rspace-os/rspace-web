/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";

import { doNotAwait } from "../../Util";

describe("doNotAwait", () => {
  describe("When passed a function that takes multiple arguments", () => {
    test("all of the subsequent arguments should be passed through.", () => {
      const sleep: (ms: number) => Promise<void> = (ms) =>
        new Promise((resolve) => setTimeout(resolve, ms));

      const sumAndSleep: (...ms: Array<number>) => Promise<void> = (...ms) =>
        sleep(ms.reduce((x, y) => x + y, 0));

      const sumTwoAndSleep: (ms1: number, ms2: number) => void =
        doNotAwait(sumAndSleep);
      expect(sumTwoAndSleep(3, 4)).toBeUndefined();

      const sumThreeAndSleep: (ms1: number, ms2: number, ms3: number) => void =
        doNotAwait(sumAndSleep);
      expect(sumThreeAndSleep(3, 4, 5)).toBeUndefined();
    });
  });
});
