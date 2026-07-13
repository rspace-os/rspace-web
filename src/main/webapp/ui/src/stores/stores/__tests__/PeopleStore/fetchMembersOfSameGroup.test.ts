import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import * as PersonMocking from "../../../models/__tests__/PersonModel/mocking";
import PersonModel from "../../../models/PersonModel";
import getRootStore from "../../getRootStore";

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
  },
}));
describe("fetchMembersOfSameGroup", () => {
  test("Error message should be returned as promise.reject", async () => {
    const restoreConsole = silenceConsole(
      ["error"],
      ["Could not fetch set of users in the same group as current user"],
    );
    const { peopleStore } = getRootStore();
    runInAction(() => {
      peopleStore.currentUser = new PersonModel(PersonMocking.personAttrs());
    });
    try {
      await peopleStore.fetchMembersOfSameGroup();
      expect.fail("Shouldn't have resolved.");
    } catch (e) {
      expect((e as Error).message).toEqual("some error message");
    } finally {
      restoreConsole();
    }
  });
});
