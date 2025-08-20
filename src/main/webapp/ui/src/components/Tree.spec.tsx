import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  SimpleTreeExample,
  MultiSelectTreeExample,
  ExpandableTreeExample,
  ControlledTreeExample,
} from "./Tree.story";
import AxeBuilder from "@axe-core/playwright";

type TreeItem = {
  id: string;
  name: string;
  children?: TreeItem[];
};

const createSelectionSpy = () => {
  let selectedItems: TreeItem | TreeItem[] | null = null;
  let selectionEvents: (TreeItem | TreeItem[] | null)[] = [];

  const handler = (items: TreeItem | TreeItem[] | null) => {
    selectedItems = items;
    selectionEvents.push(items);
  };

  const getSelectedItems = () => selectedItems;
  const getSelectionEvents = () => selectionEvents;
  const reset = () => {
    selectedItems = null;
    selectionEvents = [];
  };

  return {
    handler,
    getSelectedItems,
    getSelectionEvents,
    reset,
  };
};

const createExpansionSpy = () => {
  let expandedItems: TreeItem[] = [];
  let expansionEvents: TreeItem[][] = [];

  const handler = (items: TreeItem[]) => {
    expandedItems = items;
    expansionEvents.push([...items]);
  };

  const getExpandedItems = () => expandedItems;
  const getExpansionEvents = () => expansionEvents;
  const reset = () => {
    expandedItems = [];
    expansionEvents = [];
  };

  return {
    handler,
    getExpandedItems,
    getExpansionEvents,
    reset,
  };
};

test.describe("Tree Component", () => {
  test.describe("Single Selection", () => {
    test("Should select a single tree item", async ({ mount, page }) => {
      const selectionSpy = createSelectionSpy();
      await mount(
        <SimpleTreeExample onSelectionChange={selectionSpy.handler} />,
      );

      await page.getByRole("treeitem", { name: "Item 1" }).click();

      await expect(
        page.getByRole("treeitem", { name: "Item 1" }),
      ).toHaveAttribute("aria-selected", "true");

      const events = selectionSpy.getSelectionEvents();
      expect(events.length).toBeGreaterThan(0);
    });

    test("Should replace selection when clicking different item", async ({
      mount,
      page,
    }) => {
      const selectionSpy = createSelectionSpy();
      await mount(
        <SimpleTreeExample onSelectionChange={selectionSpy.handler} />,
      );

      await page.getByRole("treeitem", { name: "Item 1" }).click();
      await page.getByRole("treeitem", { name: "Item 2" }).click();

      await expect(
        page.getByRole("treeitem", { name: "Item 1" }),
      ).toHaveAttribute("aria-selected", "false");
      await expect(
        page.getByRole("treeitem", { name: "Item 2" }),
      ).toHaveAttribute("aria-selected", "true");
    });
  });

  test.describe("Multi Selection", () => {
    test("Should select multiple items with Ctrl+click", async ({
      mount,
      page,
    }) => {
      const selectionSpy = createSelectionSpy();
      await mount(
        <MultiSelectTreeExample onSelectionChange={selectionSpy.handler} />,
      );

      await page.getByRole("treeitem", { name: "Item 1" }).click();
      await page.getByRole("treeitem", { name: "Item 2" }).click({
        modifiers: ["ControlOrMeta"],
      });

      await expect(
        page.getByRole("treeitem", { name: "Item 1" }),
      ).toHaveAttribute("aria-selected", "true");
      await expect(
        page.getByRole("treeitem", { name: "Item 2" }),
      ).toHaveAttribute("aria-selected", "true");
    });

    test("Should clear all selections", async ({ mount, page }) => {
      const selectionSpy = createSelectionSpy();
      await mount(
        <MultiSelectTreeExample onSelectionChange={selectionSpy.handler} />,
      );

      await page.getByRole("treeitem", { name: "Item 1" }).click();
      await page.getByRole("treeitem", { name: "Item 2" }).click({
        modifiers: ["ControlOrMeta"],
      });

      await page.getByRole("button", { name: "Clear Selection" }).click();

      const selectedItems = page.locator(
        '[role="treeitem"][aria-selected="true"]',
      );
      await expect(selectedItems).toHaveCount(0);
    });
  });

  test.describe("Expansion and Collapse", () => {
    test("Should expand a tree item", async ({ mount, page }) => {
      const expansionSpy = createExpansionSpy();
      await mount(
        <ExpandableTreeExample onExpansionChange={expansionSpy.handler} />,
      );

      await page.getByRole("treeitem", { name: "Parent Item" }).click();

      await expect(
        page.getByRole("treeitem", { name: "Parent Item" }),
      ).toHaveAttribute("aria-expanded", "true");
      await expect(
        page.getByRole("treeitem", { name: "Child Item 1" }),
      ).toBeVisible();
      await expect(
        page.getByRole("treeitem", { name: "Child Item 2" }),
      ).toBeVisible();
    });

    test("Should expand all items", async ({ mount, page }) => {
      await mount(<ExpandableTreeExample />);

      await page.getByRole("button", { name: "Expand All" }).click();

      const expandableItems = page.locator('[role="treeitem"][aria-expanded]');
      const count = await expandableItems.count();
      for (let i = 0; i < count; i++) {
        await expect(expandableItems.nth(i)).toHaveAttribute(
          "aria-expanded",
          "true",
        );
      }
    });

    test("Should collapse all items", async ({ mount, page }) => {
      await mount(<ExpandableTreeExample />);

      await page.getByRole("button", { name: "Expand All" }).click();
      await page.getByRole("button", { name: "Collapse All" }).click();

      const expandableItems = page.locator('[role="treeitem"][aria-expanded]');
      const count = await expandableItems.count();
      for (let i = 0; i < count; i++) {
        await expect(expandableItems.nth(i)).toHaveAttribute(
          "aria-expanded",
          "false",
        );
      }
    });
  });

  test.describe("Controlled Tree", () => {
    test("Should handle both selection and expansion in controlled mode", async ({
      mount,
      page,
    }) => {
      const selectionSpy = createSelectionSpy();
      const expansionSpy = createExpansionSpy();
      await mount(
        <ControlledTreeExample
          onSelectionChange={selectionSpy.handler}
          onExpansionChange={expansionSpy.handler}
        />,
      );

      await page.getByRole("treeitem", { name: "Parent Item" }).click();
      await page.getByRole("treeitem", { name: "Child Item 1" }).click();

      await expect(
        page.getByRole("treeitem", { name: "Parent Item" }),
      ).toHaveAttribute("aria-expanded", "true");
      await expect(
        page.getByRole("treeitem", { name: "Child Item 1" }),
      ).toHaveAttribute("aria-selected", "true");
    });
  });

  test.describe("Accessibility", () => {
    test("Should have no axe violations", async ({ mount, page }) => {
      await mount(<SimpleTreeExample />);

      const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
      expect(
        accessibilityScanResults.violations.filter((v) => {
          return (
            v.description !==
              "Ensure elements with an ARIA role that require child roles contain them" &&
            v.id !== "landmark-one-main" &&
            v.id !== "page-has-heading-one" &&
            v.id !== "region"
          );
        }),
      ).toEqual([]);
    });

    test("Should have proper ARIA attributes for selected state", async ({
      mount,
      page,
    }) => {
      await mount(<SimpleTreeExample />);

      await page.getByRole("treeitem", { name: "Item 1" }).click();
      await expect(
        page.getByRole("treeitem", { name: "Item 1" }),
      ).toHaveAttribute("aria-selected", "true");
    });

    test("Should maintain focus management", async ({ mount, page }) => {
      await mount(<SimpleTreeExample />);

      await page.getByRole("treeitem", { name: "Item 1" }).click();
      const focusedElement = page.locator(":focus");
      await expect(focusedElement).toHaveRole("treeitem");
    });
  });

  test.describe("Keyboard Navigation", () => {
    test("Should navigate with arrow keys", async ({ mount, page }) => {
      await mount(<SimpleTreeExample />);

      await page.getByRole("treeitem", { name: "Item 1" }).focus();
      await page.keyboard.press("ArrowDown");

      const focusedElement = page.locator(":focus");
      await expect(focusedElement).toHaveAccessibleName("Item 2");
    });

    test("Should select with Space key", async ({ mount, page }) => {
      await mount(<SimpleTreeExample />);

      await page.getByRole("treeitem", { name: "Item 1" }).focus();
      await page.keyboard.press("Space");

      await expect(
        page.getByRole("treeitem", { name: "Item 1" }),
      ).toHaveAttribute("aria-selected", "true");
    });
  });
});
