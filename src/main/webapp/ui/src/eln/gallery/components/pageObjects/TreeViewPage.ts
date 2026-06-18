import { type Locator, page } from "vitest/browser";

/**
 * Page object for the gallery TreeView component as mounted by the stories in
 * TreeView.story.tsx. Encapsulates locators and user interactions; assertions
 * live in the tests themselves.
 *
 * Ported from the Playwright component-test spec to the Vitest browser-mode
 * locator API (`vitest/browser`).
 */
export class TreeViewPage {
  /**
   * Returns a locator for the tree item with the given accessible name.
   * The `name` is matched as a substring regex, mirroring the Playwright spec.
   */
  treeItem(name: string): Locator {
    return page.getByRole("treeitem", { name: new RegExp(name) });
  }

  /**
   * Clicks a tree item by name (plain click, no modifiers).
   */
  async clickTreeItem(name: string): Promise<void> {
    await this.treeItem(name).click();
  }

  /**
   * Shift-clicks a tree item.
   *
   * Uses `locator.click({ modifiers: ["Shift"] })` — the Playwright-native
   * modifiers form. This correctly sets `event.shiftKey` on the pointer event
   * so that MUI's `SimpleTreeView` shift-range selection logic fires.
   */
  async shiftClickTreeItem(name: string): Promise<void> {
    await this.treeItem(name).click({ modifiers: ["Shift"] });
  }

  /**
   * The warning toast rendered by Alerts when shift-range selection is
   * attempted in the tree view.
   *
   * MUI fires `onItemSelectionToggle` with `selected: true` for each item in
   * the shift range; `TreeView.tsx` catches each invocation where
   * `shiftKey === true && selected === true` and calls `addAlert` with
   * `variant: "warning"`. `ToastMessage` wraps the toast in a Snackbar root
   * with `role="group"` and `aria-label="warning alert"`, which is the
   * accessible node we match here.
   *
   * We do NOT match `role="alert"` (the inner `SnackbarContent`) because its
   * accessible name comes from the warning message body text — not "Error".
   */
  get shiftSelectionWarningAlert(): Locator {
    return page.getByRole("group", { name: /warning alert/i });
  }
}
