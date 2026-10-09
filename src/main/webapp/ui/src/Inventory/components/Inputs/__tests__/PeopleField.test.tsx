import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import * as PersonMocking from "../../../../stores/models/__tests__/PersonModel/mocking";
import PersonModel from "../../../../stores/models/PersonModel";
import getRootStore from "../../../../stores/stores/getRootStore";
import RsSet from "../../../../util/set";
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

  test("When restrictToUser is set, that person is the only selectable option, even when other people are known.", async () => {
    const user = userEvent.setup();
    const { peopleStore } = getRootStore();
    const currentUser = new PersonModel(
      PersonMocking.personAttrs({ id: 1, username: "owner", firstName: "Olive", lastName: "Owner" }),
    );
    const otherGroupMember = new PersonModel(
      PersonMocking.personAttrs({ id: 2, username: "other", firstName: "Oscar", lastName: "Other" }),
    );
    const requester = new PersonModel(
      PersonMocking.personAttrs({ id: 3, username: "requester", firstName: "Rae", lastName: "Requester" }),
    );
    runInAction(() => {
      peopleStore.currentUser = currentUser;
      // Already populated, so the component's own fetchMembersOfSameGroup effect short-circuits
      // without making a network call - restrictToUser bypasses this list entirely anyway.
      peopleStore.groupMembers = new RsSet([currentUser, otherGroupMember]);
    });

    render(
      <Alerts>
        <PeopleField onSelection={() => {}} recipient={null} restrictToUser={requester} />
      </Alerts>,
    );
    await user.click(screen.getByRole("button", { name: "Open" }));

    const options = screen.getAllByRole("option");
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent(requester.label);
    expect(screen.queryByText(currentUser.label, { exact: false })).not.toBeInTheDocument();
    expect(screen.queryByText(otherGroupMember.label, { exact: false })).not.toBeInTheDocument();
  });

  test("Without restrictToUser, every known person (group members and current user) is selectable.", async () => {
    const { peopleStore } = getRootStore();
    const currentUser = new PersonModel(
      PersonMocking.personAttrs({ id: 1, username: "owner", firstName: "Olive", lastName: "Owner" }),
    );
    const otherGroupMember = new PersonModel(
      PersonMocking.personAttrs({ id: 2, username: "other", firstName: "Oscar", lastName: "Other" }),
    );
    runInAction(() => {
      peopleStore.currentUser = currentUser;
      peopleStore.groupMembers = new RsSet([currentUser, otherGroupMember]);
    });

    render(
      <Alerts>
        <PeopleField onSelection={() => {}} recipient={null} />
      </Alerts>,
    );
    // No explicit opening needed: the field autofocuses on mount and, unlike the restrictToUser
    // case, openOnFocus is true here, so the listbox is already open.
    const options = await screen.findAllByRole("option");
    expect(options.map((o) => o.textContent)).toEqual(
      expect.arrayContaining([
        expect.stringContaining(currentUser.label),
        expect.stringContaining(otherGroupMember.label),
      ]),
    );
  });
});
