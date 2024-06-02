/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockContainer } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("permalinkURL", () => {
  test("When the container has not yet been saved, the permalinkURL should be null.", () => {
    const container = makeMockContainer({ id: null, globalId: null });
    expect(container.permalinkURL).toBe(null);
  });
});
