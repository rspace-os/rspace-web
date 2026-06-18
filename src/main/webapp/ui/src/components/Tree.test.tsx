import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import { expectAccessible, render, screen } from "@/__tests__/customQueries";
import { ControlledTreeExample, ExpandableTreeExample, MultiSelectTreeExample, SimpleTreeExample } from "./Tree.story";

/*
 * Converted from Tree.spec.tsx (Playwright CT). Every case runs in jsdom:
 * selection/expansion is driven by click, and keyboard navigation relies on the
 * roving-tabindex implemented by MUI's SimpleTreeView, which moves focus via
 * `element.focus()` internally. The spec never used real `Tab` traversal
 * (`page.keyboard.press("Tab")` with no prior focus) - it always called
 * `.focus()` on a treeitem first - so nothing is browser-bound.
 */

type TreeItem = {
  id: string;
  name: string;
  children?: TreeItem[];
};

const createSelectionSpy = () => {
  let selectedItems: TreeItem | TreeItem[] | null = null;
  const selectionEvents: (TreeItem | TreeItem[] | null)[] = [];
  const handler = (items: TreeItem | TreeItem[] | null) => {
    selectedItems = items;
    selectionEvents.push(items);
  };
  const getSelectedItems = () => selectedItems;
  const getSelectionEvents = () => selectionEvents;
  return {
    handler,
    getSelectedItems,
    getSelectionEvents,
  };
};

const getTreeItem = (name: string): HTMLElement => screen.getByRole("treeitem", { name });

/*
 * MUI's TreeItem registers its click handler on the inner `.MuiTreeItem-content`
 * element, not on the `<li role="treeitem">` root. In a real browser clicking
 * the row hits the content; in jsdom we must target the content element directly
 * so selection/expansion handlers fire.
 */
const getTreeItemContent = (name: string): HTMLElement => {
  const content = getTreeItem(name).querySelector(".MuiTreeItem-content");
  if (!(content instanceof HTMLElement)) {
    throw new Error(`No content element found for tree item "${name}"`);
  }
  return content;
};

describe("Tree Component", () => {
  describe("Single Selection", () => {
    test("Should select a single tree item", async () => {
      const user = userEvent.setup();
      const selectionSpy = createSelectionSpy();
      render(<SimpleTreeExample onSelectionChange={selectionSpy.handler} />);

      await user.click(getTreeItemContent("Item 1"));

      expect(getTreeItem("Item 1")).toHaveAttribute("aria-checked", "true");
      expect(selectionSpy.getSelectionEvents().length).toBeGreaterThan(0);
    });

    test("Should replace selection when clicking different item", async () => {
      const user = userEvent.setup();
      render(<SimpleTreeExample />);

      await user.click(getTreeItemContent("Item 1"));
      await user.click(getTreeItemContent("Item 2"));

      expect(getTreeItem("Item 1")).toHaveAttribute("aria-checked", "false");
      expect(getTreeItem("Item 2")).toHaveAttribute("aria-checked", "true");
    });
  });

  describe("Multi Selection", () => {
    test("Should select multiple items with Ctrl+click", async () => {
      const user = userEvent.setup();
      render(<MultiSelectTreeExample />);

      await user.click(getTreeItemContent("Item 1"));
      await user.keyboard("{Control>}");
      await user.click(getTreeItemContent("Item 2"));
      await user.keyboard("{/Control}");

      expect(getTreeItem("Item 1")).toHaveAttribute("aria-checked", "true");
      expect(getTreeItem("Item 2")).toHaveAttribute("aria-checked", "true");
    });

    test("Should clear all selections", async () => {
      const user = userEvent.setup();
      render(<MultiSelectTreeExample />);

      await user.click(getTreeItemContent("Item 1"));
      await user.keyboard("{Control>}");
      await user.click(getTreeItemContent("Item 2"));
      await user.keyboard("{/Control}");
      await user.click(screen.getByRole("button", { name: "Clear Selection" }));

      expect(document.querySelectorAll('[role="treeitem"][aria-checked="true"]')).toHaveLength(0);
    });
  });

  describe("Expansion and Collapse", () => {
    test("Should expand a tree item", async () => {
      const user = userEvent.setup();
      render(<ExpandableTreeExample />);

      // Capture the node up front: once expanded, the treeitem's accessible
      // name absorbs its children's text, so a name-based re-query would fail.
      const parent = getTreeItem("Parent Item");
      await user.click(getTreeItemContent("Parent Item"));

      expect(parent).toHaveAttribute("aria-expanded", "true");
      expect(getTreeItem("Child Item 1")).toBeVisible();
      expect(getTreeItem("Child Item 2")).toBeVisible();
    });

    test("Should expand all items", async () => {
      const user = userEvent.setup();
      render(<ExpandableTreeExample />);

      await user.click(screen.getByRole("button", { name: "Expand All" }));

      const expandableItems = document.querySelectorAll('[role="treeitem"][aria-expanded]');
      expect(expandableItems.length).toBeGreaterThan(0);
      expandableItems.forEach((item) => {
        expect(item).toHaveAttribute("aria-expanded", "true");
      });
    });

    test("Should collapse all items", async () => {
      const user = userEvent.setup();
      render(<ExpandableTreeExample />);

      await user.click(screen.getByRole("button", { name: "Expand All" }));
      await user.click(screen.getByRole("button", { name: "Collapse All" }));

      const expandableItems = document.querySelectorAll('[role="treeitem"][aria-expanded]');
      expect(expandableItems.length).toBeGreaterThan(0);
      expandableItems.forEach((item) => {
        expect(item).toHaveAttribute("aria-expanded", "false");
      });
    });
  });

  describe("Controlled Tree", () => {
    test("Should handle both selection and expansion in controlled mode", async () => {
      const user = userEvent.setup();
      render(<ControlledTreeExample />);

      // Capture the parent node up front: once expanded, its accessible name
      // absorbs the children's text, so a name-based re-query would fail.
      const parent = getTreeItem("Parent Item");
      await user.click(getTreeItemContent("Parent Item"));
      await user.click(getTreeItemContent("Child Item 1"));

      expect(parent).toHaveAttribute("aria-expanded", "true");
      expect(getTreeItem("Child Item 1")).toHaveAttribute("aria-checked", "true");
    });
  });

  describe("Accessibility", () => {
    test("Should have no axe violations", async () => {
      const { baseElement } = render(<SimpleTreeExample />);

      await expectAccessible(baseElement);
    });

    test("Should have proper ARIA attributes for selected state", async () => {
      const user = userEvent.setup();
      render(<SimpleTreeExample />);

      await user.click(getTreeItemContent("Item 1"));

      expect(getTreeItem("Item 1")).toHaveAttribute("aria-checked", "true");
    });

    test("Should maintain focus management", async () => {
      const user = userEvent.setup();
      render(<SimpleTreeExample />);

      await user.click(getTreeItemContent("Item 1"));

      expect(document.activeElement).toHaveAttribute("role", "treeitem");
    });
  });

  describe("Keyboard Navigation", () => {
    test("Should navigate with arrow keys", async () => {
      const user = userEvent.setup();
      render(<SimpleTreeExample />);

      const item1 = getTreeItem("Item 1");
      item1.focus();
      await user.keyboard("{ArrowDown}");

      expect(getTreeItem("Item 2")).toHaveFocus();
    });

    test("Should select with Space key", async () => {
      const user = userEvent.setup();
      render(<SimpleTreeExample />);

      const item1 = getTreeItem("Item 1");
      item1.focus();
      await user.keyboard(" ");

      expect(getTreeItem("Item 1")).toHaveAttribute("aria-checked", "true");
    });
  });
});
