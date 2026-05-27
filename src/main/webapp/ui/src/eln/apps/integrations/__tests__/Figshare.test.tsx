import userEvent from "@testing-library/user-event";
import { test, describe, expect } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import Figshare from "../Figshare";
import { Optional } from "../../../../util/optional";
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
        />,
      );
      await userEvent.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  test("Should have a connect button when the user is not authenticated.", async () => {
    render(
      <Figshare
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.empty(),
          },
        }}
        update={() => {}}
      />,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(
      screen.getByRole("button", {
        name: /connect/i,
      }),
    ).toBeVisible();
  });
  test("Should have a disconnect button when the user is authenticated.", async () => {
    render(
      <Figshare
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.present("some token"),
          },
        }}
        update={() => {}}
      />,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(
      screen.getByRole("button", {
        name: /disconnect/i,
      }),
    ).toBeVisible();
  });
});
