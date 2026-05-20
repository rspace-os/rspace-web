import React from "react";
import { describe, expect, test } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  ControlledTreeExample,
  ExpandableTreeExample,
  MultiSelectTreeExample,
  SimpleTreeExample,
} from "../Tree.story";

function getContent(name: string): HTMLElement {
  const treeItem = screen.getByRole("treeitem", { name });
  // eslint-disable-next-line testing-library/no-node-access
  const content = treeItem.querySelector(".MuiTreeItem-content");
  if (!(content instanceof HTMLElement)) {
    throw new Error(`Tree item content for ${name} not found`);
  }
  return content;
}

function getTreeItemBySuffix(suffix: string): HTMLElement {
  // eslint-disable-next-line testing-library/no-node-access
  const treeItem = document.querySelector(`[role="treeitem"][id$="-${suffix}"]`);
  if (!(treeItem instanceof HTMLElement)) {
    throw new Error(`Tree item ${suffix} not found`);
  }
  return treeItem;
}

describe("Tree", () => {
  test("selects and replaces the selected item", async () => {
    const user = userEvent.setup();
    render(<SimpleTreeExample />);

    const item1 = screen.getByRole("treeitem", { name: "Item 1" });
    const item2 = screen.getByRole("treeitem", { name: "Item 2" });

    await user.click(getContent("Item 1"));
    expect(item1).toHaveAttribute("aria-selected", "true");

    await user.click(getContent("Item 2"));
    expect(item1).toHaveAttribute("aria-selected", "false");
    expect(item2).toHaveAttribute("aria-selected", "true");
  });

  test("supports multi-select and clearing the selection", async () => {
    const user = userEvent.setup();
    render(<MultiSelectTreeExample />);

    const item1 = screen.getByRole("treeitem", { name: "Item 1" });
    const item2 = screen.getByRole("treeitem", { name: "Item 2" });

    await user.click(getContent("Item 1"));
    fireEvent.click(getContent("Item 2"), { ctrlKey: true });

    expect(item1).toHaveAttribute("aria-selected", "true");
    expect(item2).toHaveAttribute("aria-selected", "true");

    await user.click(screen.getByRole("button", { name: "Clear Selection" }));
    expect(item1).toHaveAttribute("aria-selected", "false");
    expect(item2).toHaveAttribute("aria-selected", "false");
  });

  test("expands and collapses all expandable items", async () => {
    const user = userEvent.setup();
    render(<ExpandableTreeExample />);

    await user.click(screen.getByRole("button", { name: "Expand All" }));
    expect(getTreeItemBySuffix("parent")).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(getTreeItemBySuffix("parent2")).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(getTreeItemBySuffix("child4")).toHaveAttribute(
      "aria-expanded",
      "true",
    );

    await user.click(screen.getByRole("button", { name: "Collapse All" }));
    expect(getTreeItemBySuffix("parent")).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(getTreeItemBySuffix("parent2")).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(getTreeItemBySuffix("child4")).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });

  test("handles selection and expansion in controlled mode", async () => {
    const user = userEvent.setup();
    render(<ControlledTreeExample />);

    await user.click(screen.getByRole("button", { name: "Expand All" }));
    await user.click(getContent("Child Item 1"));

    expect(
      screen.getByText((content) => content.includes("Expanded: Parent Item")),
    ).toBeVisible();
    expect(
      screen.getByText((content) => content.includes("Selected: Child Item 1")),
    ).toBeVisible();
  });

  test("supports keyboard navigation and selection", async () => {
    const user = userEvent.setup();
    render(<SimpleTreeExample />);

    const item1 = screen.getByRole("treeitem", { name: "Item 1" });
    const item2 = screen.getByRole("treeitem", { name: "Item 2" });

    item1.focus();
    await user.keyboard("{ArrowDown}");
    expect(item2).toHaveFocus();

    await user.keyboard(" ");
    expect(item2).toHaveAttribute("aria-selected", "true");
  });

  test("is accessible", async () => {
    const { baseElement } = render(<SimpleTreeExample />);
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });
});
