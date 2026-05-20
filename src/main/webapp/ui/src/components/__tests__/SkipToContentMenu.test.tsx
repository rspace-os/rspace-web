import { describe, expect, test } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/matchMedia";
import {
  DynamicLandmarksExample,
  SimpleTestExample,
} from "../SkipToContentMenu.story";

describe("SkipToContentMenu", () => {
  test("shows the skip links when focused", async () => {
    const user = userEvent.setup();

    render(<SimpleTestExample />);

    await user.tab();

    expect(screen.getByRole("button", { name: "Skip to Header" })).toHaveFocus();
    expect(screen.getByRole("button", { name: "Skip to Header" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Skip to Footer" })).toBeVisible();
  });

  test("moves focus to the chosen landmark", async () => {
    const user = userEvent.setup();

    render(<SimpleTestExample />);

    await user.tab();
    await user.keyboard("{Enter}");

    expect(screen.getByText("Header Content").parentElement).toHaveFocus();
  });

  test("supports arrow-key navigation between landmarks", async () => {
    const user = userEvent.setup();

    render(<SimpleTestExample />);

    await user.tab();
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "Skip to Footer" })).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(screen.getByRole("button", { name: "Skip to Header" })).toHaveFocus();
  });

  test("updates the list when extra landmarks are registered", async () => {
    const user = userEvent.setup();

    render(<DynamicLandmarksExample />);

    await user.click(screen.getByRole("button", { name: /show extra landmarks/i }));
    await user.tab();

    expect(screen.getByRole("button", { name: "Skip to Sidebar" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Skip to Comments" })).toBeVisible();
  });

  test("closes the menu on escape", async () => {
    const user = userEvent.setup();

    render(<SimpleTestExample />);

    await user.tab();
    const menu = screen.getByRole("menu", {
      name: /skip to content navigation/i,
    });
    expect(menu).toBeVisible();

    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(menu).not.toBeVisible();
    });
  });
});
