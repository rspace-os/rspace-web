/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer, containerAttrs } from "./mocking";
import InvApiService from "../../../../common/InvApiService";
import { type AxiosResponse, type AxiosRequestConfig } from "@/common/axios";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  query: vi.fn(() => {}),

  }}));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
  },
})
}));

describe("fetchAdditionalInfo", () => {
  it("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const container = makeMockContainer();
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
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


