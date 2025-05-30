/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSample, sampleAttrs } from "./mocking";
import { makeMockTemplate } from "../TemplateModel/mocking";
import InvApiService from "../../../../common/InvApiService";

const mockRootStore = {
  unitStore: {
    assertValidUnitId: () => {},
    getUnit: () => ({ label: "ml" }),
  },
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
  },
  searchStore: {
    getTemplate: jest.fn(() => Promise.reject()),
  },
};

jest.mock("../../../../common/InvApiService", () => ({
  query: () => ({}),
}));
jest.mock("../../../../stores/stores/RootStore", () => () => mockRootStore);

describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const template = makeMockTemplate();
    mockRootStore.searchStore.getTemplate.mockImplementation(() =>
      Promise.resolve(template)
    );

    const sample = makeMockSample({
      templateId: 1,
    });
    jest.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: {
          ...sampleAttrs(),
          templateId: 1,
        },
      })
    );

    let firstCallDone = false;
    void sample.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });

    await sample.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
    /*
     * The fetching of the sample's template should also complete before
     * fetchAdditionalInfo resolves
     */
    expect(sample.template).toEqual(template);
  });
  test("Calls made on a sample without a template should resolve.", async () => {
    const sample = makeMockSample();
    jest.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: sampleAttrs(),
      })
    );
    await expect(sample.fetchAdditionalInfo()).resolves.toBeUndefined();
  });
});
