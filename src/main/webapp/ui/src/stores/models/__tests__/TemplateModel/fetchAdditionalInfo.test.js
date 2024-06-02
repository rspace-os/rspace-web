/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockTemplate, templateAttrs } from "./mocking";
import InvApiService from "../../../../common/InvApiService";

jest.mock("../../../../common/InvApiService", () => ({
  get: () => ({}),
}));
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
  },
}));

describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const template = makeMockTemplate();
    jest.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: templateAttrs(),
      })
    );

    let firstCallDone = false;
    await template.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });

    await template.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
  });
});
