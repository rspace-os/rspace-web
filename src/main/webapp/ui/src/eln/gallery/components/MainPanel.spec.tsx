import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { suppressFireAndForget404, worker } from "@/__tests__/browserSetup";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { BunchOfImages, NestedFoldersWithImageFile } from "./MainPanel.story";
import { MainPanelPage } from "./pageObjects/MainPanelPage";

/*
 * CLIPBOARD: Clipboard APIs require a browser permission grant that is not
 * available in Vitest browser mode. We stub `navigator.clipboard.writeText`
 * before each test that needs it, capture the written value in a local
 * variable, and assert on it. The stub is restored in afterEach.
 *
 * MODIFIER CLICKS: Use `locator.click({ modifiers: [...] })` — the
 * `userEvent.keyboard` pointer-hold form is unreliable (vitest-browser #7007).
 *
 * ANIMATIONS: `page.evaluate(() => document.getAnimations()...)` becomes a
 * direct `document.getAnimations()` call wrapped in `expect.poll` (tests run
 * in-browser so there is no page-eval indirection needed).
 *
 * KEYBOARD: `page.keyboard.press("Shift+ArrowRight")` becomes
 * `userEvent.keyboard("{Shift>}{ArrowRight}{/Shift}")`.
 *
 * REDUCED MOTION (axe test): `page.emulateMedia` is CDP-only. We emulate via
 * CDP on Chromium so the cards render at their final opacity immediately.
 * On Firefox/WebKit CDP is unavailable; the media emulation step is skipped
 * but the axe scan still runs.
 */

const panel = new MainPanelPage();

// ── Clipboard stub ────────────────────────────────────────────────────────────
// Shared state for stub; populated only during clipboard tests.
let capturedClipboardText: string | null = null;
let originalWriteText: typeof navigator.clipboard.writeText | null = null;

function installClipboardStub(): void {
  capturedClipboardText = null;
  originalWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
  navigator.clipboard.writeText = (text: string) => {
    capturedClipboardText = text;
    return Promise.resolve();
  };
}

function uninstallClipboardStub(): void {
  if (originalWriteText !== null) {
    navigator.clipboard.writeText = originalWriteText;
    originalWriteText = null;
  }
  capturedClipboardText = null;
}

// ── Per-suite MSW handlers ─────────────────────────────────────────────────────

function linkedDocumentsHandler() {
  return http.get("/gallery/ajax/getLinkedDocuments/:id", () =>
    HttpResponse.json({
      data: [],
      error: null,
      success: true,
      errorMsg: null,
    }),
  );
}

/*
 * When the TreeView expands the Outer folder (id=1), it calls
 * `useGalleryListing` which fetches `/gallery/getUploadedFiles?currentFolderId=1&...`.
 *
 * This handler MUST be registered before `galleryAppShellHandlers()` in the
 * `worker.use(...)` call because the app-shell handlers contain a wildcard catch-all
 * for `/gallery/getUploadedFiles` that returns an empty listing. MSW resolves
 * handlers in registration order (first match wins within a single `worker.use()`
 * call), so our specific folder handler must appear first.
 */
function outerFolderListingHandler() {
  return http.get("/gallery/getUploadedFiles", ({ request }) => {
    const url = new URL(request.url);
    if (url.searchParams.get("currentFolderId") === "1") {
      return HttpResponse.json({
        data: {
          items: {
            totalHits: 1,
            totalPages: 1,
            results: [
              {
                id: 2,
                oid: { idString: "GF2" },
                name: "Inner folder",
                ownerName: "user1",
                description: null,
                creationDate: 1672531200,
                modificationDate: 1672531200,
                type: "Folder",
                extension: null,
                thumbnailId: null,
                size: 12345,
                version: 1,
                originalImageOid: { idString: "GF2" },
              },
            ],
          },
          parentId: 1,
        },
        error: null,
        success: true,
        errorMsg: null,
      });
    }
    // Let other handlers (including the galleryAppShellHandlers catch-all) handle it.
    return undefined;
  });
}

// Restores the fire-and-forget 404 suppressor installed in beforeEach.
let restoreFireAndForget404: (() => void) | undefined;

/*
 * This suite pins the viewport to 1280×720 (see beforeEach). The viewport is a
 * property of the shared browser instance, not of this file's iframe, so it
 * would otherwise leak to whichever spec file runs next and skew its layout-
 * dependent assertions. Capture the original size once and restore it after the
 * suite so file ordering cannot make another suite flaky.
 */
let originalViewport: { width: number; height: number } | undefined;
beforeAll(() => {
  originalViewport = { width: window.innerWidth, height: window.innerHeight };
});
afterAll(async () => {
  if (originalViewport) {
    await page.viewport(originalViewport.width, originalViewport.height);
  }
});

beforeEach(async () => {
  /*
   * The tree-view and clipboard tests expand folders and select files, which
   * fire folder-listing / thumbnail / linked-document requests the component
   * does not await. One of these can still be in flight when the test ends; the
   * `resetHandlers()` in browserSetup then drops its mock, so it 404s against
   * the real server after teardown and surfaces as an unhandled rejection that
   * fails the run (seen only under slower CI). Opt this suite into swallowing
   * exactly those benign gallery 404s; every other unhandled rejection still
   * fails.
   */
  restoreFireAndForget404 = suppressFireAndForget404([
    "/gallery/getUploadedFiles",
    "/gallery/getThumbnail",
    "/gallery/ajax/getLinkedDocuments",
  ]);

  /*
   * Pin the viewport to 1280×720 for consistent grid column counts. The grid
   * computes its column count from the MUI breakpoint of the window width
   * (`cols = 12 / cardWidth[viewportSize]`): at 1280 the breakpoint is "lg" →
   * 4 columns, which is what the rectangular shift-range selection expectations
   * below assume. Browser mode's default viewport is wider ("xl" → 6 columns),
   * which would change the selected region. Other test files do not set a
   * viewport, so this is scoped to MainPanel.
   */
  await page.viewport(1280, 720);

  /*
   * IMPORTANT: outerFolderListingHandler must be registered BEFORE
   * galleryAppShellHandlers() because the app-shell handlers contain a
   * wildcard catch-all for `/gallery/getUploadedFiles` that would otherwise
   * intercept the folder-specific request first.
   */
  worker.use(oauthTokenHandler(), linkedDocumentsHandler(), outerFolderListingHandler(), ...galleryAppShellHandlers());
});

afterEach(() => {
  restoreFireAndForget404?.();
  uninstallClipboardStub();
  cleanup();
});

describe("MainPanel", () => {
  test("Should have no axe violations", async () => {
    render(<NestedFoldersWithImageFile />);

    // Wait for the grid to appear before running the axe scan.
    await expect.element(panel.gridCell("Outer folder")).toBeVisible();

    /*
     * FileCards fade in with a per-card staggered transitionDelay. axe's
     * color-contrast check trips if it scans mid-fade, when a card is at a low
     * opacity and therefore low contrast. Snap every running transition/
     * animation to its end so the cards sit at their final opacity before the
     * scan. This is deterministic and works on every engine, unlike CDP
     * `prefers-reduced-motion` emulation, which is Chromium-only and so left
     * WebKit/Firefox scanning a mid-fade frame (intermittently, under slower CI).
     */
    for (const animation of document.getAnimations()) {
      try {
        animation.finish();
      } catch {
        // Indefinite/infinite animations cannot be finished; ignore them.
      }
    }

    await expectNoAxeViolations();
  });

  describe("breadcrumbs", () => {
    test("The root of the gallery section", async () => {
      render(<NestedFoldersWithImageFile />);
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();

      const items = panel.breadcrumbItems();
      expect(items.elements().length).toBe(1);
      await expect.element(items.nth(0)).toHaveTextContent("Images");
    });

    test("A outer folder is opened", async () => {
      render(<NestedFoldersWithImageFile />);
      await panel.dblClickFile("Outer folder");
      await expect.element(panel.gridCell("Inner folder")).toBeVisible();

      const items = panel.breadcrumbItems();
      expect(items.elements().length).toBe(2);
      await expect.element(items.nth(0)).toHaveTextContent("Images");
      await expect.element(items.nth(1)).toHaveTextContent("Outer folder");
    });

    test("Selecting the inner folder alters the breadcrumbs", async () => {
      render(<NestedFoldersWithImageFile />);
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();

      await panel.switchToTreeView();

      // Expand the outer folder in the tree view
      await expect.element(panel.treeItem("Outer folder")).toBeVisible();
      await panel.expandTreeItem("Outer folder");
      await expect.element(panel.treeItem("Inner folder")).toBeVisible();

      // Select the inner folder
      await panel.selectTreeItem("Inner folder");

      // Breadcrumbs should show Images > Outer folder
      const items = panel.breadcrumbItems();
      expect(items.elements().length).toBe(2);
      await expect.element(items.nth(0)).toHaveTextContent("Images");
      await expect.element(items.nth(1)).toHaveTextContent("Outer folder");
    });

    test("Tapping the root gallery section breadcrumb works as a link", async () => {
      render(<NestedFoldersWithImageFile />);
      await panel.dblClickFile("Outer folder");
      await expect.element(panel.gridCell("Inner folder")).toBeVisible();

      await panel.breadcrumbButton("Images").click();

      // Root gallery section is returned to
      const items = panel.breadcrumbItems();
      expect(items.elements().length).toBe(1);
      await expect.element(panel.breadcrumbButton("Images")).toHaveTextContent("Images");
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();
    });

    test("Tapping the outer folder breadcrumb works as a link", async () => {
      render(<NestedFoldersWithImageFile />);
      await panel.dblClickFile("Outer folder");
      await expect.element(panel.gridCell("Inner folder")).toBeVisible();
      await panel.dblClickFile("Inner folder");

      // Wait for breadcrumbs to update to show two items after navigating into inner folder
      await vi.waitFor(
        () => {
          const items = panel.breadcrumbItems().elements();
          if (items.length < 2) throw new Error("Breadcrumbs not updated to 2 items yet");
        },
        { timeout: 5000, interval: 50 },
      );

      await panel.breadcrumbButton("Outer folder").click();

      // Should be back to outer folder showing two breadcrumb items
      const items = panel.breadcrumbItems();
      expect(items.elements().length).toBe(2);
      await expect.element(panel.breadcrumbButton("Images")).toHaveTextContent("Images");
      await expect.element(panel.breadcrumbButton("Outer folder")).toHaveTextContent("Outer folder");
    });
  });

  describe("Copy-to-clipboard button and tree-view", () => {
    /*
     * Clipboard tests use a JS stub — no browser permissions needed, and the
     * tests run on all browsers (no webkit skip).
     */

    test("Nothing is selected — clipboard contains the gallery-section link", async () => {
      installClipboardStub();
      render(<NestedFoldersWithImageFile />);
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();

      await panel.switchToTreeView();
      await expect.element(panel.treeItem("Outer folder")).toBeVisible();
      await panel.expandTreeItem("Outer folder");
      await expect.element(panel.treeItem("Inner folder")).toBeVisible();

      await panel.copyToClipboardButton.click();
      await expect.element(panel.clipboardSuccessAlert()).toBeVisible();

      expect(capturedClipboardText).toMatch(/\?mediaType=Images/);
    });

    test("A outer folder is selected — clipboard contains the gallery-section link", async () => {
      installClipboardStub();
      render(<NestedFoldersWithImageFile />);
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();

      await panel.switchToTreeView();
      await expect.element(panel.treeItem("Outer folder")).toBeVisible();
      await panel.expandTreeItem("Outer folder");
      await expect.element(panel.treeItem("Inner folder")).toBeVisible();

      await panel.selectTreeItem("Outer folder");

      await panel.copyToClipboardButton.click();
      await expect.element(panel.clipboardSuccessAlert()).toBeVisible();

      expect(capturedClipboardText).toMatch(/\?mediaType=Images/);
    });

    test("The inner folder is selected — clipboard contains a link to the file's parent folder", async () => {
      installClipboardStub();
      render(<NestedFoldersWithImageFile />);
      await expect.element(panel.gridCell("Outer folder")).toBeVisible();

      await panel.switchToTreeView();
      await expect.element(panel.treeItem("Outer folder")).toBeVisible();
      await panel.expandTreeItem("Outer folder");
      await expect.element(panel.treeItem("Inner folder")).toBeVisible();

      await panel.selectTreeItem("Inner folder");

      await panel.copyToClipboardButton.click();
      await expect.element(panel.clipboardSuccessAlert()).toBeVisible();

      /*
       * When the user selects a file inside a folder when using tree view, and
       * the breadcrumbs update to show the path to the selected file, the adjacent
       * copy button should refer to the parent folder of the selected file and not
       * the current root folder.
       */
      expect(capturedClipboardText).toMatch(/\/gallery\/1/);
    });
  });

  describe("Grid view", () => {
    describe("Selection", () => {
      test("When a file is tapped, it should become selected", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");

        await assertSelection(["Image0.jpg"]);
      });

      test("Ctrl-clicking on a second file, adds it to the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image1.jpg", {
          modifiers: ["ControlOrMeta"],
        });

        await assertSelection(["Image0.jpg", "Image1.jpg"]);
      });

      test("Shift-clicking on a second file selects the region", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image5.jpg", { modifiers: ["Shift"] });

        await assertSelection(["Image0.jpg", "Image1.jpg", "Image4.jpg", "Image5.jpg"]);
      });

      test("Shift-clicking a second time modifies the selection based on the first click", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image1.jpg")).toBeVisible();

        await panel.clickFile("Image1.jpg");
        await panel.clickFile("Image6.jpg", { modifiers: ["Shift"] });
        await panel.clickFile("Image7.jpg", { modifiers: ["Shift"] });

        await assertSelection(["Image1.jpg", "Image2.jpg", "Image3.jpg", "Image5.jpg", "Image6.jpg", "Image7.jpg"]);

        await panel.clickFile("Image4.jpg", { modifiers: ["Shift"] });

        await assertSelection(["Image0.jpg", "Image1.jpg", "Image4.jpg", "Image5.jpg"]);
      });

      test("Ctrl-clicking an unselected file after shift-clicking several files should expand the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image5.jpg", { modifiers: ["Shift"] });
        await panel.clickFile("Image6.jpg", { modifiers: ["ControlOrMeta"] });

        await assertSelection(["Image0.jpg", "Image1.jpg", "Image4.jpg", "Image5.jpg", "Image6.jpg"]);
      });

      test("Ctrl-clicking a selected file after shift-clicking several files should reduce the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image5.jpg", { modifiers: ["Shift"] });
        await panel.clickFile("Image1.jpg", { modifiers: ["ControlOrMeta"] });

        await assertSelection(["Image0.jpg", "Image4.jpg", "Image5.jpg"]);
      });

      test("Pressing an arrow key moves the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await pressKey("ArrowRight");

        await assertSelection(["Image1.jpg"]);
      });

      test("Pressing an arrow key after selecting multiple files with ctrl moves the selection relative to the second selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image1.jpg", { modifiers: ["ControlOrMeta"] });
        await pressKey("ArrowRight");

        await assertSelection(["Image2.jpg"]);
      });

      test("Pressing an arrow key after selecting multiple files with shift moves the selection relative to the second selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image1.jpg", { modifiers: ["Shift"] });
        await pressKey("ArrowRight");

        await assertSelection(["Image2.jpg"]);
      });

      test("Pressing shift-arrow key after selecting multiple files with ctrl expands the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image1.jpg", { modifiers: ["ControlOrMeta"] });
        await assertSelection(["Image0.jpg", "Image1.jpg"]);

        await pressKey("Shift+ArrowRight");

        await assertSelection(["Image0.jpg", "Image1.jpg", "Image2.jpg"]);
      });

      test("Pressing shift-arrow key after selecting multiple files with shift expands the selection", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        await panel.clickFile("Image0.jpg");
        await panel.clickFile("Image1.jpg", { modifiers: ["Shift"] });
        await assertSelection(["Image0.jpg", "Image1.jpg"]);

        await pressKey("Shift+ArrowRight");

        await assertSelection(["Image0.jpg", "Image1.jpg", "Image2.jpg"]);
      });

      test("Shift-arrowing a second time modifies the selection based on the first click", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image1.jpg")).toBeVisible();

        await panel.clickFile("Image1.jpg");
        await pressKey("Shift+ArrowRight");
        await assertSelection(["Image1.jpg", "Image2.jpg"]);

        await pressKey("Shift+ArrowLeft");
        await pressKey("Shift+ArrowLeft");
        await assertSelection(["Image0.jpg", "Image1.jpg"]);
      });

      test("The name of a selected file has a background colour", async () => {
        render(<BunchOfImages />);
        await expect.element(panel.gridCell("Image0.jpg")).toBeVisible();

        // Before selection: unselected colours
        const nameEl = panel.gridCell("Image0.jpg").getByRole("paragraph");
        await expect.element(nameEl).toHaveStyle({ color: "rgb(75, 71, 77)" });
        await expect.element(nameEl).toHaveStyle({ "background-color": "rgba(0, 0, 0, 0)" });

        // After selecting Image0.jpg: selected colours
        await panel.clickFile("Image0.jpg");
        await expect.element(nameEl).toHaveStyle({ color: "rgb(38, 75, 88)" });
        await expect.element(nameEl).toHaveStyle({ "background-color": "rgb(147, 198, 240)" });

        /*
         * This is done to ensure that we are not using colour alone to indicate
         * selection. The border colour changes to indicate selection but it is
         * possible that this will be imperceptable to some users. The background
         * and text colours of the file name, however, completely switch places
         * which should suffice.
         */
      });
    });
  });
});

// ── Shared helpers ────────────────────────────────────────────────────────────

/**
 * Asserts that the given set of items is selected in the grid.
 *
 * Checks:
 * 1. Each named gridcell carries `aria-selected="true"`.
 * 2. The selection-status text in the listing-controls region accounts for
 *    exactly `expectedSelection.length` items total.
 */
async function assertSelection(expectedSelection: string[]): Promise<void> {
  for (const item of expectedSelection) {
    await expect.element(panel.gridCell(item)).toHaveAttribute("aria-selected", "true");
  }
  // Poll the status text because MobX state + React render is async.
  await expect
    .poll(() => {
      const statusEl = document.querySelector('[role="status"]');
      return statusEl?.textContent ?? "";
    })
    .toMatch(new RegExp(`(${expectedSelection.length} file|${expectedSelection.length} folder)`));
}

/**
 * Presses a keyboard key (with optional Shift modifier) via `userEvent.keyboard`.
 *
 * Translates Playwright's `"Shift+ArrowRight"` notation to the
 * `userEvent.keyboard` `{Shift>}{ArrowRight}{/Shift}` form.
 */
async function pressKey(key: "ArrowRight" | "Shift+ArrowRight" | "ArrowLeft" | "Shift+ArrowLeft"): Promise<void> {
  /*
   * Ensure the grid's roving-tabindex card holds focus before dispatching the
   * key, so the keydown bubbles up to the grid container's `onKeyDown` handler.
   * The component focuses this card itself (a `useEffect` keyed on
   * `tabIndexCoord`), but that runs asynchronously: after a click + MobX/React
   * re-render the active element can momentarily fall back to `<body>`, which
   * would swallow the arrow key and leave the selection unchanged (the test
   * then polls until timeout). Re-focusing the card the grid renders with
   * `tabindex="0"` makes arrow-key navigation deterministic across engines.
   */
  await expect.poll(() => document.querySelector('[role="grid"] [tabindex="0"]') instanceof HTMLElement).toBe(true);
  const activeCard = document.querySelector('[role="grid"] [tabindex="0"]');
  if (activeCard instanceof HTMLElement) activeCard.focus();

  if (key.includes("+")) {
    const [modifier, base] = key.split("+");
    await userEvent.keyboard(`{${modifier}>}{${base}}{/${modifier}}`);
  } else {
    await userEvent.keyboard(`{${key}}`);
  }
}
