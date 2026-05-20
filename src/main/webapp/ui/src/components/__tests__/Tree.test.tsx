import { describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { fireEvent } from "@testing-library/react";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/matchMedia";
import {
  ControlledTreeExample,
  ExpandableTreeExample,
  MultiSelectTreeExample,
  SimpleTreeExample,
} from "../Tree.story";

describe("Tree", () => {
  test("supports single selection and replaces the current selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(<SimpleTreeExample onSelectionChange={onSelectionChange} />);

    await user.click(screen.getByRole("treeitem", { name: /Item 1/i }));
    expect(screen.getByRole("treeitem", { name: /Item 1/i })).toHaveAttribute(
      "aria-selected",
      "true",
    );

    await user.click(screen.getByRole("treeitem", { name: /Item 2/i }));
    expect(screen.getByRole("treeitem", { name: /Item 1/i })).toHaveAttribute(
      "aria-selected",
      "false",
    );
    expect(screen.getByRole("treeitem", { name: /Item 2/i })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(onSelectionChange).toHaveBeenCalled();
  });

  test("supports multi-select and clearing the selection", async () => {
    const user = userEvent.setup();

    render(<MultiSelectTreeExample />);

    const item1 = screen.getByRole("treeitem", { name: /Item 1/i });
    const item2 = screen.getByRole("treeitem", { name: /Item 2/i });

    await user.click(item1);
    fireEvent.click(item2, { ctrlKey: true });

    expect(item1).toHaveAttribute("aria-selected", "true");
    expect(item2).toHaveAttribute("aria-selected", "true");

    await user.click(screen.getByRole("button", { name: /clear selection/i }));
    expect(item1).toHaveAttribute("aria-selected", "false");
    expect(item2).toHaveAttribute("aria-selected", "false");
  });

  test("expands and collapses tree items", async () => {
    const user = userEvent.setup();

    render(<ExpandableTreeExample />);

    const parent = screen.getByRole("treeitem", { name: /Parent Item/i });
    await user.click(parent);
    await waitFor(() => {
      expect(screen.getByRole("treeitem", { name: /Child Item 1/i })).toBeVisible();
    });

    await user.click(screen.getByRole("button", { name: /Expand All/i }));
    expect(screen.getByRole("treeitem", { name: /Grandchild 1/i })).toBeVisible();

    await user.click(screen.getByRole("button", { name: /Collapse All/i }));
    await waitFor(() => {
      expect(screen.queryByRole("treeitem", { name: /Grandchild 1/i })).not.toBeInTheDocument();
    });
  });

  test("supports controlled selection and expansion", async () => {
    const user = userEvent.setup();

    render(<ControlledTreeExample />);

    await user.click(screen.getByRole("treeitem", { name: /Parent Item/i }));
    await waitFor(() => {
      expect(screen.getByRole("treeitem", { name: /Child Item 1/i })).toBeVisible();
    });
    await user.click(screen.getByRole("treeitem", { name: /Child Item 1/i }));

    expect(
      screen.getByText(/Selected: Child Item 1/i),
    ).toBeVisible();
  });

  test("supports keyboard navigation and selection", async () => {
    const user = userEvent.setup();

    render(<SimpleTreeExample />);

    const item1 = screen.getByRole("treeitem", { name: /Item 1/i });
    item1.focus();
    expect(item1).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("treeitem", { name: /Item 2/i })).toHaveFocus();

    await user.keyboard(" ");
    expect(screen.getByRole("treeitem", { name: /Item 2/i })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  test("is accessible", async () => {
    const { baseElement } = render(<SimpleTreeExample />);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
  });
});
