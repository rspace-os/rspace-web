import React from "react";
import { describe, expect, test } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  DynamicLandmarksExample,
  SimpleTestExample,
} from "../SkipToContentMenu.story";

describe("SkipToContentMenu", () => {
  test("becomes visible when focused and shows landmark options", async () => {
    const user = userEvent.setup();
    render(<SimpleTestExample />);

    await user.tab();

    const headerButton = screen.getByRole("button", { name: "Skip to Header" });
    await waitFor(() => {
      expect(headerButton).toBeVisible();
    });
    expect(headerButton).toBeVisible();
    expect(screen.getByRole("button", { name: "Skip to Footer" })).toBeVisible();
  });

  test("allows navigation to a landmark", async () => {
    const user = userEvent.setup();
    render(<SimpleTestExample />);

    await user.tab();
    await user.keyboard("{Enter}");

    // eslint-disable-next-line testing-library/no-node-access
    expect(screen.getByText("Header Content").parentElement).toHaveFocus();
  });

  test("supports arrow key navigation through landmarks", async () => {
    const user = userEvent.setup();
    render(<SimpleTestExample />);

    await user.tab();
    const headerButton = screen.getByRole("button", { name: "Skip to Header" });
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "Skip to Footer" })).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(headerButton).toHaveFocus();
  });

  test("updates when landmarks are registered dynamically", async () => {
    const user = userEvent.setup();
    render(<DynamicLandmarksExample />);

    await user.click(screen.getByRole("button", { name: /show extra landmarks/i }));
    await user.tab();
    await user.tab();

    const headerButton = screen.getByRole("button", { name: "Skip to Header" });
    await waitFor(() => {
      expect(headerButton).toBeVisible();
    });
    expect(screen.getByRole("button", { name: "Skip to Sidebar" })).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Skip to Comments" }),
    ).toBeVisible();
  });

  test("closes the menu on escape", async () => {
    const user = userEvent.setup();
    render(<SimpleTestExample />);

    const menu = screen.getByRole("menu", {
      name: "Skip to content navigation",
    });
    await user.tab();
    const headerButton = screen.getByRole("button", { name: "Skip to Header" });
    await waitFor(() => {
      expect(headerButton).toBeVisible();
    });

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(menu).toHaveStyle({ opacity: "0" });
    });
  });
});
