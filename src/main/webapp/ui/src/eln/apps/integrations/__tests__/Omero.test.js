/*
 * @jest-environment jsdom
 */
//@flow strict
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
import Omero from "../Omero";
import { axe, toHaveNoViolations } from "jest-axe";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Omero", () => {
  test("Should have no axe violations.", async () => {
    const { baseElement } = render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: {},
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(await screen.findByRole("dialog")).toBeVisible();

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(baseElement)).toHaveNoViolations();
  });
  test("Should render username and password fields.", () => {
    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: {},
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("textbox", { name: "Username" })).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Password" })).toBeVisible();
  });
});
