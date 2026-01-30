/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import React from "react";
import {
  render,
  screen,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import Figshare from "../Figshare";
import { Optional } from "../../../../util/optional";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";
import "../../../../../__mocks__/matchMedia";

expect.extend({ toHaveNoViolations });

beforeEach(() => {
  vi.clearAllMocks();
});


describe("Figshare", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <Figshare
          integrationState={{
            mode: "DISABLED",
            credentials: {
              ACCESS_TOKEN: Optional.empty(),
            },
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(await screen.findByRole("dialog")).toBeVisible();

      expect(await axe(baseElement)).toHaveNoViolations();
    });
  });
  test("Should have a connect button when the user is not authenticated.", () => {
    render(
      <Figshare
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.empty(),
          },
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();
  });
  test("Should have a disconnect button when the user is authenticated.", () => {
    render(
      <Figshare
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.present("some token"),
          },
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
  });
});


