/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import "@testing-library/jest-dom/vitest";
import getRootStore from "../../RootStore";
import * as PersonMocking from "../../../models/__tests__/PersonModel/mocking";
import PersonModel from "../../../models/PersonModel";
import { runInAction } from "mobx";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";

beforeEach(() => {
  vi.clearAllMocks();
});

vi.mock("../../../../common/ElnApiService", () => ({
  default: {
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

  }}));

describe("fetchMembersOfSameGroup", () => {
  it("Error message should be returned as promise.reject", async () => {
    const restoreConsole = silenceConsole(["error"], [/./]);
    const { peopleStore } = getRootStore();
    runInAction(() => {
      peopleStore.currentUser = new PersonModel(PersonMocking.personAttrs());
    });
    try {
      await peopleStore.fetchMembersOfSameGroup();
      fail("Shouldn't have resolved.");
    } catch (e) {
      expect((e as Error).message).toEqual("some error message");
    } finally {
      restoreConsole();
    }
  });
});
