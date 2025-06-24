/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import getRootStore from "../../RootStore";
import * as PersonMocking from "../../../models/__tests__/PersonModel/mocking";
import PersonModel from "../../../models/PersonModel";
import { runInAction } from "mobx";

beforeEach(() => {
  jest.clearAllMocks();
});

jest.mock("../../../../common/ElnApiService", () => ({
  get: () => {
    return Promise.resolve({
      data: {
        status: "INTERNAL_SERVER_ERROR",
        httpCode: 500,
        internalCode: 50001,
        message: "some error message",
        messageCode: null,
        errors: ["General server error"],
        iso8601Timestamp: "2024-01-04T13:05:32.773681492Z",
        data: null,
      },
    });
  },
}));

describe("fetchMembersOfSameGroup", () => {
  test("Error message should be returned as promise.reject", async () => {
    const { peopleStore } = getRootStore();
    runInAction(() => {
      peopleStore.currentUser = new PersonModel(PersonMocking.personAttrs());
    });
    try {
      await peopleStore.fetchMembersOfSameGroup();
      // $FlowExpectedError[cannot-resolve-name] Global variable
      fail("Shouldn't have resolved.");
    } catch (e) {
      expect((e as Error).message).toEqual("some error message");
    }
  });
});
