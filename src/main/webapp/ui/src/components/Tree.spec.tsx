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
const feature = test.extend<{
  Given: {
    "a simple tree is rendered": () => Promise<{
      selectionSpy: ReturnType<typeof createSelectionSpy>;
    }>;
    "a multi-select tree is rendered": () => Promise<{
      selectionSpy: ReturnType<typeof createSelectionSpy>;
    }>;
    "an expandable tree is rendered": () => Promise<{
      expansionSpy: ReturnType<typeof createExpansionSpy>;
    }>;
    "a controlled tree is rendered": () => Promise<{
      selectionSpy: ReturnType<typeof createSelectionSpy>;
      expansionSpy: ReturnType<typeof createExpansionSpy>;
    }>;
  };
  When: {
    "the user clicks on tree item {string}": (
      itemName: string,
    ) => Promise<void>;
    "the user ctrl+clicks on tree item {string}": (
      itemName: string,
    ) => Promise<void>;
    "the user clicks the Clear Selection button": () => Promise<void>;
    "the user clicks the Expand All button": () => Promise<void>;
    "the user clicks the Collapse All button": () => Promise<void>;
    "the user focuses on tree item {string}": (
      itemName: string,
    ) => Promise<void>;
    "the user presses ArrowDown": () => Promise<void>;
    "the user presses Space": () => Promise<void>;
  };
  Then: {
    "tree item {string} should be selected": (
      itemName: string,
    ) => Promise<void>;
    "tree item {string} should not be selected": (
      itemName: string,
    ) => Promise<void>;
    "tree item {string} should be expanded": (
      itemName: string,
    ) => Promise<void>;
    "tree item {string} should be collapsed": (
      itemName: string,
    ) => Promise<void>;
    "tree item {string} should be visible": (itemName: string) => Promise<void>;
    "tree item {string} should be focused": (itemName: string) => Promise<void>;
    "no tree items should be selected": () => Promise<void>;
    "all expandable items should be expanded": () => Promise<void>;
    "all expandable items should be collapsed": () => Promise<void>;
    "selection events should have been triggered": ({
      selectionSpy,
    }: {
      selectionSpy: ReturnType<typeof createSelectionSpy>;
    }) => void;
    "there should be no axe violations": () => Promise<void>;
    "the focused element should have role treeitem": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "a simple tree is rendered": async () => {
        const selectionSpy = createSelectionSpy();
        await mount(
          <SimpleTreeExample onSelectionChange={selectionSpy.handler} />,
        );
        return { selectionSpy };
      },
      "a multi-select tree is rendered": async () => {
        const selectionSpy = createSelectionSpy();
        await mount(
          <MultiSelectTreeExample onSelectionChange={selectionSpy.handler} />,
        );
        return { selectionSpy };
      },
      "an expandable tree is rendered": async () => {
        const expansionSpy = createExpansionSpy();
        await mount(
          <ExpandableTreeExample onExpansionChange={expansionSpy.handler} />,
        );
        return { expansionSpy };
      },
      "a controlled tree is rendered": async () => {
        const selectionSpy = createSelectionSpy();
        const expansionSpy = createExpansionSpy();
        await mount(
          <ControlledTreeExample
            onSelectionChange={selectionSpy.handler}
            onExpansionChange={expansionSpy.handler}
          />,
        );
        return { selectionSpy, expansionSpy };
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks on tree item {string}": async (itemName: string) => {
        await page.getByRole("treeitem", { name: itemName }).click();
      },
      "the user ctrl+clicks on tree item {string}": async (
        itemName: string,
      ) => {
        await page.getByRole("treeitem", { name: itemName }).click({
          modifiers: ["ControlOrMeta"],
        });
      },
      "the user clicks the Clear Selection button": async () => {
        await page.getByRole("button", { name: "Clear Selection" }).click();
      },
      "the user clicks the Expand All button": async () => {
        await page.getByRole("button", { name: "Expand All" }).click();
      },
      "the user clicks the Collapse All button": async () => {
        await page.getByRole("button", { name: "Collapse All" }).click();
      },
      "the user focuses on tree item {string}": async (itemName: string) => {
        await page.getByRole("treeitem", { name: itemName }).focus();
      },
      "the user presses ArrowDown": async () => {
        await page.keyboard.press("ArrowDown");
      },
      "the user presses Space": async () => {
        await page.keyboard.press("Space");
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "tree item {string} should be selected": async (itemName: string) => {
        await expect(
          page.getByRole("treeitem", { name: itemName }),
        ).toHaveAttribute("aria-selected", "true");
      },
      "tree item {string} should not be selected": async (itemName: string) => {
        await expect(
          page.getByRole("treeitem", { name: itemName }),
        ).toHaveAttribute("aria-selected", "false");
      },
      "tree item {string} should be expanded": async (itemName: string) => {
        await expect(
          page.getByRole("treeitem", { name: itemName }),
        ).toHaveAttribute("aria-expanded", "true");
      },
      "tree item {string} should be collapsed": async (itemName: string) => {
        await expect(
          page.getByRole("treeitem", { name: itemName }),
        ).toHaveAttribute("aria-expanded", "false");
      },
      "tree item {string} should be visible": async (itemName: string) => {
        await expect(
          page.getByRole("treeitem", { name: itemName }),
        ).toBeVisible();
      },
      "tree item {string} should be focused": async (itemName: string) => {
        const focusedElement = page.locator(":focus");
        await expect(focusedElement).toHaveAccessibleName(itemName);
      },
      "no tree items should be selected": async () => {
        const selectedItems = page.locator(
          '[role="treeitem"][aria-selected="true"]',
        );
        await expect(selectedItems).toHaveCount(0);
      },
      "all expandable items should be expanded": async () => {
        const expandableItems = page.locator(
          '[role="treeitem"][aria-expanded]',
        );
        const count = await expandableItems.count();
        for (let i = 0; i < count; i++) {
          await expect(expandableItems.nth(i)).toHaveAttribute(
            "aria-expanded",
            "true",
          );
        }
      },
      "all expandable items should be collapsed": async () => {
        const expandableItems = page.locator(
          '[role="treeitem"][aria-expanded]',
        );
        const count = await expandableItems.count();
        for (let i = 0; i < count; i++) {
          await expect(expandableItems.nth(i)).toHaveAttribute(
            "aria-expanded",
            "false",
          );
        }
      },
      "selection events should have been triggered": ({ selectionSpy }) => {
        const events = selectionSpy.getSelectionEvents();
        expect(events.length).toBeGreaterThan(0);
      },
      "there should be no axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
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
      },
      "the focused element should have role treeitem": async () => {
        const focusedElement = page.locator(":focus");
        await expect(focusedElement).toHaveRole("treeitem");
      },
    });
  },
});
test.describe("Tree Component", () => {
  test.describe("Single Selection", () => {
    feature(
      "Should select a single tree item",
      async ({ Given, When, Then }) => {
        const { selectionSpy } = await Given["a simple tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Item 1");
        await Then["tree item {string} should be selected"]("Item 1");
        Then["selection events should have been triggered"]({ selectionSpy });
      },
    );
    feature(
      "Should replace selection when clicking different item",
      async ({ Given, When, Then }) => {
        await Given["a simple tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Item 1");
        await When["the user clicks on tree item {string}"]("Item 2");
        await Then["tree item {string} should not be selected"]("Item 1");
        await Then["tree item {string} should be selected"]("Item 2");
      },
    );
  });
  test.describe("Multi Selection", () => {
    feature(
      "Should select multiple items with Ctrl+click",
      async ({ Given, When, Then }) => {
        await Given["a multi-select tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Item 1");
        await When["the user ctrl+clicks on tree item {string}"]("Item 2");
        await Then["tree item {string} should be selected"]("Item 1");
        await Then["tree item {string} should be selected"]("Item 2");
      },
    );
    feature("Should clear all selections", async ({ Given, When, Then }) => {
      await Given["a multi-select tree is rendered"]();
      await When["the user clicks on tree item {string}"]("Item 1");
      await When["the user ctrl+clicks on tree item {string}"]("Item 2");
      await When["the user clicks the Clear Selection button"]();
      await Then["no tree items should be selected"]();
    });
  });
  test.describe("Expansion and Collapse", () => {
    feature("Should expand a tree item", async ({ Given, When, Then }) => {
      await Given["an expandable tree is rendered"]();
      await When["the user clicks on tree item {string}"]("Parent Item");
      await Then["tree item {string} should be expanded"]("Parent Item");
      await Then["tree item {string} should be visible"]("Child Item 1");
      await Then["tree item {string} should be visible"]("Child Item 2");
    });
    feature("Should expand all items", async ({ Given, When, Then }) => {
      await Given["an expandable tree is rendered"]();
      await When["the user clicks the Expand All button"]();
      await Then["all expandable items should be expanded"]();
    });
    feature("Should collapse all items", async ({ Given, When, Then }) => {
      await Given["an expandable tree is rendered"]();
      await When["the user clicks the Expand All button"]();
      await When["the user clicks the Collapse All button"]();
      await Then["all expandable items should be collapsed"]();
    });
  });
  test.describe("Controlled Tree", () => {
    feature(
      "Should handle both selection and expansion in controlled mode",
      async ({ Given, When, Then }) => {
        await Given["a controlled tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Parent Item");
        await When["the user clicks on tree item {string}"]("Child Item 1");
        await Then["tree item {string} should be expanded"]("Parent Item");
        await Then["tree item {string} should be selected"]("Child Item 1");
      },
    );
  });
  test.describe("Accessibility", () => {
    feature("Should have no axe violations", async ({ Given, Then }) => {
      await Given["a simple tree is rendered"]();
      await Then["there should be no axe violations"]();
    });
    feature(
      "Should have proper ARIA attributes for selected state",
      async ({ Given, When, Then }) => {
        await Given["a simple tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Item 1");
        await Then["tree item {string} should be selected"]("Item 1");
      },
    );
    feature(
      "Should maintain focus management",
      async ({ Given, When, Then }) => {
        await Given["a simple tree is rendered"]();
        await When["the user clicks on tree item {string}"]("Item 1");
        await Then["the focused element should have role treeitem"]();
      },
    );
  });
  test.describe("Keyboard Navigation", () => {
    feature(
      "Should navigate with arrow keys",
      async ({ Given, When, Then }) => {
        await Given["a simple tree is rendered"]();
        await When["the user focuses on tree item {string}"]("Item 1");
        await When["the user presses ArrowDown"]();
        await Then["tree item {string} should be focused"]("Item 2");
      },
    );
    feature("Should select with Space key", async ({ Given, When, Then }) => {
      await Given["a simple tree is rendered"]();
      await When["the user focuses on tree item {string}"]("Item 1");
      await When["the user presses Space"]();
      await Then["tree item {string} should be selected"]("Item 1");
    });
  });
});
});
