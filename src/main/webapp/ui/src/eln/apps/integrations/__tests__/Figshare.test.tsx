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
  screen,
  fireEvent,
} from "@testing-library/react";
import Figshare from "../Figshare";
import { Optional } from "../../../../util/optional";
import "../../../../../__mocks__/matchMedia";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("Figshare", () => {
  describe("Accessibility", () => {
    it("Should have no axe violations.", async () => {
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

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  it("Should have a connect button when the user is not authenticated.", () => {
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
  it("Should have a disconnect button when the user is authenticated.", () => {
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
