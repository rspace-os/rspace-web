/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import { doNotAwait } from "../../Util";

describe("doNotAwait", () => {
  describe("When passed a function that takes multiple arguments", () => {
    test("all of the subsequent arguments should be passed through.", () => {
      const sleep: (number) => Promise<void> = (ms) =>
        new Promise((resolve) => setTimeout(resolve, ms));

      const sumAndSleep: (...Array<number>) => Promise<void> = (...ms) =>
        sleep(ms.reduce((x, y) => x + y, 0));

      const sumTwoAndSleep: (number, number) => void = doNotAwait(sumAndSleep);
      expect(sumTwoAndSleep(3, 4)).toBeUndefined();

      const sumThreeAndSleep: (number, number, number) => void =
        doNotAwait(sumAndSleep);
      expect(sumThreeAndSleep(3, 4, 5)).toBeUndefined();
    });
  });
});
