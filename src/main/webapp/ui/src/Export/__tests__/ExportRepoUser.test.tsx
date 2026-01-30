/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import ExportRepoUser from "../ExportRepoUser";
import { type Person } from "../repositories/common";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const mockAxios = new MockAdapter(axios, { onNoMatch: "throwException" });

beforeEach(() => {
  vi.clearAllMocks();
});


function renderExportRepoUser({
  people,
  updatePeople,
}: {
  updatePeople?: (people: Array<Person>) => void;
  people?: Array<Person>;
} = {}) {
  return render(
    <ExportRepoUser
      initialPeople={people ?? []}
      updatePeople={updatePeople ?? (() => {})}
      inputValidations={{ author: true, contact: true }}
      submitAttempt={false}
    />
  );
}

describe("ExportRepoUser", () => {
  it("If no people are passed as prop, then the current user should be fetched.", async () => {
    mockAxios.onGet("/directory/ajax/subject").reply(200, {
      data: {
        email: "joe.bloggs@example.com",
        fullName: "Joe Bloggs",
      },
    });

    const updatePeople = vi.fn<[Array<Person>], unknown[]>();
    await act(() => renderExportRepoUser({ people: [], updatePeople }));
    expect(updatePeople).toHaveBeenCalled();
  });

  it("If people are passed as prop, current user is not fetched.", async () => {
    // `/directory/ajax/subject` is not mocked so that if ExportRepoUser
    // attempts to the make a call the test fails

    const updatePeople = vi.fn<[Array<Person>], unknown[]>();
    await act(() =>
      renderExportRepoUser({
        people: [
          {
            email: "joe.bloggs@example.com",
            type: "author",
            uniqueName: "Joe Bloggs",
          },
        ],
        updatePeople,
      })
    );
    expect(updatePeople).not.toHaveBeenCalled();
  });
});


