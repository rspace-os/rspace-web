/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import DryadRepo from "../DryadRepo";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DryadRepo", () => {
  /*
   * Call this function like so to avoid the "not wrapped in act(...)" warning:
   *   await act(() => renderDryadRepo());
   *
   * This is because that after its initial render, DyradRepo makes a network
   * call to /directory/ajax/subject, which then triggers a re-rendering when
   * it completes.
   */
  const renderDryadRepo = ({
    handleChange,
  }: { handleChange?: () => void } = {}) => {
    return render(
      <DryadRepo
        repo={{
          repoName: "app.dryad",
          displayName: "Dryad",
          subjects: [],
          license: {
            licenseRequired: false,
            otherLicensePermitted: false,
            licenses: [],
          },
          repoCfg: -1,
          linkedDMPs: [],
        }}
        handleChange={handleChange ?? (() => {})}
        handleCrossrefFunderChange={() => {}}
        handleFetchCrossrefFunder={() => {}}
        crossrefFunders={[]}
        inputValidations={{
          description: true,
          title: true,
          author: true,
          contact: true,
          subject: true,
          crossrefFunder: true,
        }}
        submitAttempt={false}
        updatePeople={() => {}}
        contacts={[]}
        authors={[]}
      />
    );
  };

  /*
   * We then want to mock that network call so that it is not attempted for
   * real in the test runtime.
   */
  mockAxios.onGet("/directory/ajax/subject").reply(200, {
    data: {
      email: "joe.bloggs@example.com",
      fullName: "Joe Bloggs",
    },
  });

  test("Upon editing, title should be set to the entered value.", async () => {
    const handleChange = jest.fn();
    await act(() => void renderDryadRepo({ handleChange }));

    fireEvent.change(screen.getByRole("textbox", { name: /Title/ }), {
      target: { value: "foo" },
    });

    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "title",
        }),
      })
    );
  });

  test("Upon editing, description should be set to the entered value.", async () => {
    const handleChange = jest.fn();
    await act(() => void renderDryadRepo({ handleChange }));

    fireEvent.change(screen.getByRole("textbox", { name: /Add an abstract/ }), {
      target: { value: "foo" },
    });

    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "description",
        }),
      })
    );
  });
});
