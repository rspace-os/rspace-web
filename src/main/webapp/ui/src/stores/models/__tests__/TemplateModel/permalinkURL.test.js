/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockTemplate } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("permalinkURL", () => {
  test("When the template has not yet been saved, the permalinkURL should be null.", () => {
    const template = makeMockTemplate({ id: null, globalId: null });
    expect(template.permalinkURL).toBe(null);
  });
});
