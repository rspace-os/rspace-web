import React from "react";
import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  HorizontalExample,
  SimpleExample,
} from "./useOneDimensionalRovingTabIndex.story";

describe("useOneDimensionalRovingTabIndex", () => {
  test("tabs into and through the simple example", async () => {
    const user = userEvent.setup();
    render(<SimpleExample />);

    const beforeButton = screen.getByRole("button", { name: "Before the list" });
    const firstItem = screen.getByRole("button", { name: "One Thing" });
    const afterButton = screen.getByRole("button", { name: "After the list" });

    await user.tab();
    expect(beforeButton).toHaveFocus();

    await user.tab();
    expect(firstItem).toHaveFocus();

    await user.tab();
    expect(afterButton).toHaveFocus();
  });

  test("moves through the vertical list with arrow keys and remembers focus", async () => {
    const user = userEvent.setup();
    render(<SimpleExample />);

    const beforeButton = screen.getByRole("button", { name: "Before the list" });
    const firstItem = screen.getByRole("button", { name: "One Thing" });
    const secondItem = screen.getByRole("button", { name: "Two Thing" });

    await user.tab();
    await user.tab();
    expect(firstItem).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(secondItem).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(firstItem).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(secondItem).toHaveFocus();

    await user.tab({ shift: true });
    expect(beforeButton).toHaveFocus();

    await user.tab();
    expect(secondItem).toHaveFocus();
  });

  test("moves through the horizontal example with left and right arrows", async () => {
    const user = userEvent.setup();
    render(<HorizontalExample />);

    const firstItem = screen.getByRole("button", { name: "One Thing" });
    const secondItem = screen.getByRole("button", { name: "Two Thing" });

    await user.tab();
    await user.tab();
    expect(firstItem).toHaveFocus();

    await user.keyboard("{ArrowRight}");
    expect(secondItem).toHaveFocus();

    await user.keyboard("{ArrowLeft}");
    expect(firstItem).toHaveFocus();
  });
});
