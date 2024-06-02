/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import PeopleField from "../PeopleField";
import Alerts from "../../Alerts";
import getRootStore from "../../../../stores/stores/RootStore";
import * as PersonMocking from "../../../../stores/models/__tests__/PersonModel/mocking";
import PersonModel from "../../../../stores/models/PersonModel";
import { runInAction } from "mobx";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

jest.mock("../../../../common/ElnApiService", () => ({
  get: (endpoint) => {
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

describe("PeopleField", () => {
  test("When the API returns an error, there should be an error alert.", async () => {
    const { peopleStore } = getRootStore();
    runInAction(() => {
      peopleStore.currentUser = new PersonModel(PersonMocking.personAttrs());
    });

    render(
      <Alerts>
        <PeopleField onSelection={() => {}} label="foo" recipient={null} />
      </Alerts>
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "some error message"
    );
  });
});
