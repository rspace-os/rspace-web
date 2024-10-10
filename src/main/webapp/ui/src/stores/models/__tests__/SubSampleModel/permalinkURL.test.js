/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSubSample } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
}));

describe("permalinkURL", () => {
  test("When the subsample has not yet been saved, the permalinkURL should be null.", () => {
    const subsample = makeMockSubSample({ id: null, globalId: null });
    expect(subsample.permalinkURL).toBe(null);
  });
});
