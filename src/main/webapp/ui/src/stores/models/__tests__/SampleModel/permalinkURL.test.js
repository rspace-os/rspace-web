/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSample } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("permalinkURL", () => {
  test("When the sample has not yet been saved, the permalinkURL should be null.", () => {
    const sample = makeMockSample({ id: null, globalId: null });
    expect(sample.permalinkURL).toBe(null);
  });
});
