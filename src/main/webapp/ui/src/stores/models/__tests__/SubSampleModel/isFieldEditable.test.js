/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSubSample } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {});

describe("isFieldEditable", () => {
  describe("When the Subsample is deleted, isFieldEditable should", () => {
    test("return false for notes.", () => {
      const subsample = makeMockSubSample({
        deleted: true,
      });

      expect(subsample.isFieldEditable("notes")).toBe(false);
    });
  });
});
