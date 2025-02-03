/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import { makeMockSample } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
}));

describe("method: validate", () => {
  describe("Asserts expiry date.", () => {
    test("Returns false when expiry date is an invalid date.", () => {
      const sample = makeMockSample({
        expiryDate: "2021-13-01",
      });

      expect(sample.validate().isOk).toBe(false);
    });
  });
});
