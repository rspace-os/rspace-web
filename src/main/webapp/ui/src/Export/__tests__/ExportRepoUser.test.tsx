import { act, render } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import ExportRepoUser from "../ExportRepoUser";
import type { Person } from "../repositories/common";

const mockAxios = new MockAdapter(axios, { onNoMatch: "throwException" });
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
    />,
  );
}
describe("ExportRepoUser", () => {
  test("If no people are passed as prop, then the current user should be fetched.", async () => {
    mockAxios.onGet("/directory/ajax/subject").reply(200, {
      data: {
        email: "joe.bloggs@example.com",
        fullName: "Joe Bloggs",
      },
    });

    const updatePeople = vi.fn<(people: Array<Person>) => void>();
    await act(() => renderExportRepoUser({ people: [], updatePeople }));
    expect(updatePeople).toHaveBeenCalled();
  });
  test("If people are passed as prop, current user is not fetched.", async () => {
    // `/directory/ajax/subject` is not mocked so that if ExportRepoUser
    // attempts to the make a call the test fails

    const updatePeople = vi.fn<(people: Array<Person>) => void>();
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
      }),
    );
    expect(updatePeople).not.toHaveBeenCalled();
  });
});
