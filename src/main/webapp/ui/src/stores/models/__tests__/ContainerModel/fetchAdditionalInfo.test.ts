/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockContainer, containerAttrs } from "./mocking";
import InvApiService from "../../../../common/InvApiService";
import { type AxiosResponse, type AxiosRequestConfig } from "@/common/axios";

jest.mock("../../../../common/InvApiService", () => ({
  query: jest.fn(() => {}),
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
    const container = makeMockContainer();
    jest.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: containerAttrs(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as AxiosRequestConfig,
      } as AxiosResponse)
    );

    let firstCallDone = false;
    await container.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });

    await container.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
  });
});
