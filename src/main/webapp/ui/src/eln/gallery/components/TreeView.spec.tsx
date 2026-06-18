import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { suppressFireAndForget404, worker } from "@/__tests__/browserSetup";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";
import { TreeViewPage } from "./pageObjects/TreeViewPage";
import { TreeViewWithFiles } from "./TreeView.story";

/*
 * Vitest Browser Mode port of TreeView.spec.tsx (Playwright CT).
 *
 * The Shift+click case must run in a real browser because MUI's SimpleTreeView
 * shift-range selection relies on the browser focus/selection model and
 * item-ordering computation, which does not run under jsdom.
 */

const treeView = new TreeViewPage();

// Restores the fire-and-forget 404 suppressor installed in beforeEach.
let restoreFireAndForget404: (() => void) | undefined;

beforeEach(() => {
  /*
   * The tree fires folder-listing and thumbnail requests on expansion that the
   * component does not await. One can still be in flight when the test ends; the
   * `resetHandlers()` in browserSetup then drops its mock, so it 404s after
   * teardown and surfaces as an unhandled rejection that fails the run (seen on
   * Firefox). Opt into swallowing exactly those benign gallery 404s.
   */
  restoreFireAndForget404 = suppressFireAndForget404([
    "/gallery/getUploadedFiles",
    "/gallery/getThumbnail",
    "/gallery/ajax/getLinkedDocuments",
  ]);
  worker.use(oauthTokenHandler(), ...galleryAppShellHandlers());
});

afterEach(() => {
  restoreFireAndForget404?.();
  cleanup();
});

describe("TreeView", () => {
  describe("Multi-Selection", () => {
    test("Should handle Shift+click without crashing", async () => {
      render(<TreeViewWithFiles />);

      /*
       * Wait for all three tree items to be present in the DOM before
       * interacting. MUI's SimpleTreeView registers item ordering in a
       * React.useEffect that runs after each render; asserting all items are
       * visible ensures those effects have settled before we attempt selection.
       */
      await expect.element(treeView.treeItem("Test Folder")).toBeVisible();
      await expect.element(treeView.treeItem("test-image.jpg")).toBeVisible();
      await expect.element(treeView.treeItem("test-document.pdf")).toBeVisible();

      /*
       * Wait until MUI's `TreeViewChildrenItemProvider` passive effect has run
       * and populated `itemOrderedChildrenIdsLookup`. The reliable DOM signal is
       * `tabindex="0"` on the first treeitem: MUI's focus plugin derives the
       * "default focusable item" from `orderedRootItemIds`; when that list is
       * empty (before the effect runs) every item gets `tabindex="-1"`. Once the
       * passive effect fires and the store re-renders, LOCAL_1 gets
       * `tabindex="0"`. Shift-range selection (`expandSelectionRange` →
       * `getNonDisabledItemsInRange`) traverses `itemOrderedChildrenIdsLookup`
       * and throws "Invalid range" if it is still empty, so we must not proceed
       * until this attribute is present.
       */
      await vi.waitFor(
        () => {
          const tree = document.querySelector('[role="tree"]');
          if (!tree) throw new Error("Tree not found");
          const items = tree.querySelectorAll('[role="treeitem"]');
          if (items.length < 3) throw new Error("Not all items rendered yet");
          const hasTabbableItem = Array.from(items).some((item) => item.getAttribute("tabindex") === "0");
          if (!hasTabbableItem) throw new Error("Item ordering not yet populated (no tabindex=0 treeitem)");
        },
        { timeout: 5000, interval: 50 },
      );

      /*
       * Click a non-folder item first to establish a selection anchor without
       * expanding any folder node. Clicking "Test Folder" (LOCAL_1) would
       * trigger MUI's default `expansionTrigger='content'` behaviour, causing
       * the folder to expand. An empty expanded folder has no ordered children
       * in `itemOrderedChildrenIdsLookup`, so `getNextItem` inside
       * `getNonDisabledItemsInRange` would return `undefined` and throw
       * "Invalid range" when the shift-range traversal tries to step through it.
       * Clicking a plain file (LOCAL_2) selects it without expanding anything.
       */
      await treeView.clickTreeItem("test-image.jpg");

      // Shift-click on "test-document.pdf" to trigger MUI's shift-range
      // selection; this invokes `onItemSelectionToggle` with `selected: true`
      // for the range (LOCAL_2 → LOCAL_3), which the component catches and
      // surfaces as a warning alert.
      await treeView.shiftClickTreeItem("test-document.pdf");

      await expect.element(treeView.shiftSelectionWarningAlert).toBeVisible();
    });
  });
});
