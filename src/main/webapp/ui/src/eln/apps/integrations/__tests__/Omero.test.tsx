/*
 * @vitest-environment jsdom
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import Omero from "../Omero";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";
import "../../../../../__mocks__/matchMedia";

expect.extend({ toHaveNoViolations });

beforeEach(() => {
  vi.clearAllMocks();
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
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    expect(screen.getByLabelText("Password")).toBeVisible();
  });
});


