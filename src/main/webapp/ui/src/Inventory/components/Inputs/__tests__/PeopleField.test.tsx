import { render, screen } from "@testing-library/react";
import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import * as PersonMocking from "../../../../stores/models/__tests__/PersonModel/mocking";
import PersonModel from "../../../../stores/models/PersonModel";
import getRootStore from "../../../../stores/stores/getRootStore";
import Alerts from "../../Alerts";
import PeopleField from "../PeopleField";

vi.mock("../../../../common/ElnApiService", () => ({
  default: {
    get: (_endpoint: string) => {
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
describe("PeopleField", () => {
  test("When the API returns an error, there should be an error alert.", async () => {
    const restoreConsole = silenceConsole(
      ["error"],
      ["Could not fetch set of users in the same group as current user"],
    );
    const { peopleStore } = getRootStore();
    runInAction(() => {
      peopleStore.currentUser = new PersonModel(PersonMocking.personAttrs());
    });
    try {
      render(
        <Alerts>
          <PeopleField onSelection={() => {}} label="foo" recipient={null} />
        </Alerts>,
      );
      expect(await screen.findByRole("alert")).toHaveTextContent("some error message");
    } finally {
      restoreConsole();
    }
  });
});
