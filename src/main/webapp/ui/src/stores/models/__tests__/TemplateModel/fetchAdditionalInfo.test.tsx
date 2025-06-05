/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockTemplate, templateAttrs } from "./mocking";
import InvApiService from "../../../../common/InvApiService";
import { AxiosResponse } from "@/common/axios";

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
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse)
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
