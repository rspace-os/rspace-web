/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockTemplate, templateAttrs } from "./mocking";
import InvApiService from "../../../../common/InvApiService";
import { AxiosResponse } from "@/common/axios";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  get: () => ({}),

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
    const template = makeMockTemplate();
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
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


