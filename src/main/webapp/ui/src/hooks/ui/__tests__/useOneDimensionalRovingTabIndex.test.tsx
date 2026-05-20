import { describe, expect, test } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
import {
  HorizontalExample,
  SimpleExample,
} from "../useOneDimensionalRovingTabIndex.story";

describe("useOneDimensionalRovingTabIndex", () => {
  test("tabs through the list between the before and after buttons", async () => {
    const user = userEvent.setup();

    render(<SimpleExample />);

    await user.tab();
    expect(
      screen.getByRole("button", { name: "Before the list" }),
    ).toHaveFocus();

    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.tab();
    expect(
      screen.getByRole("button", { name: "After the list" }),
    ).toHaveFocus();
  });

  test("moves focus through the vertical list with arrow keys", async () => {
    const user = userEvent.setup();

    render(<SimpleExample />);

    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();
  });

  test("moves focus through the horizontal list with left and right arrows", async () => {
    const user = userEvent.setup();

    render(<HorizontalExample />);

    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();

    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();
  });
});
