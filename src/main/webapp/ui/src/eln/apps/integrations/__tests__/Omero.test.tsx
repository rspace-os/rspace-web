import { test, describe, expect } from 'vitest';
import React from "react";
import {
  render,
  screen,
  fireEvent,
} from "@testing-library/react";
import Omero from "../Omero";
import "../../../../../__mocks__/matchMedia";
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
    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible();
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
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    expect(screen.getByLabelText("Password")).toBeVisible();
  });
});
