import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { Optional } from "../../../../util/optional";
import Figshare from "../Figshare";

import "@/__tests__/__mocks__/matchMedia";

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

      fireEvent.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
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
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "apps:actions.connect" })).toBeVisible();
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
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
  });
});
