/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import Search from "../../Search";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";

jest.mock("../../../../common/InvApiService", () => ({
  query: () =>
    Promise.resolve({
      data: {
        totalHits: 0,
        records: [],
        containers: [],
      },
    }),
}));
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  uiStore: {
    addAlert: () => {},
  },
  peopleStore: {
    getUser: jest.fn(() => {
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve(null);
        }, 100);
      });
    }),
    getPersonFromBenchId: jest.fn(() => {
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve(null);
        }, 100);
      });
    }),
  },
}));

describe("setupAndPerformInitialSearch", () => {
  test("A second call whilst the first is being processed should cancel the first.", async () => {
    const search = new Search({
      factory: mockFactory(),
    });

    const promise1 = search.setupAndPerformInitialSearch({
      parentGlobalId: "BE1",
    });
    const promise2 = search.setupAndPerformInitialSearch({
      resultType: "CONTAINER",
    });
    await Promise.all([promise1, promise2]);

    // there should be nothing left of the first search's parameters
    expect(search.fetcher.resultType).toEqual("CONTAINER");
    expect(search.fetcher.parentGlobalId).toEqual(null);
  });
});
