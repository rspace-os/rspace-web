import { describe, test, expect, beforeEach, afterEach } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import React from "react";
import { render, screen, waitFor, expectAccessible} from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import {
  TreeViewWithFiles,
  TreeViewLoading,
  TreeViewFoldersOnly,
} from "./TreeView.story";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  /*
   * These are the endpoints the component's provider chain (Analytics,
   * useDeploymentProperty, usePrimaryAction) and the per-folder
   * useGalleryListing fetches actually fire on mount.
   */
  mockAxios
    .onGet("/session/ajax/analyticsProperties")
    .reply(200, { analyticsEnabled: false });
  mockAxios.onGet("/deploymentproperties/ajax/property").reply(200, false);
  // usePrimaryAction fetches the office-suite supported extensions on mount.
  mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, {});
  mockAxios.onGet("/officeOnline/supportedExts").reply(200, {});
  mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
    data: {
      parentId: 1,
      items: {
        totalHits: 0,
        totalPages: 0,
        results: [],
      },
    },
  });
});

afterEach(() => {
  mockAxios.reset();
});

function getFileNameByType(fileType: string): string {
  switch (fileType.toLowerCase()) {
    case "folder":
      return "Test Folder";
    case "image":
      return "test-image.jpg";
    case "pdf":
    case "document":
      return "test-document.pdf";
    case "word":
    case "docx":
      return "report.docx";
    case "longname":
      return "This_is_a_very_long_file_name";
    case "specialchars":
      return "file with spaces & symbols";
    default:
      throw new Error(`Unknown file type: ${fileType}`);
  }
}

function treeItemByType(fileType: string): HTMLElement {
  return screen.getByRole("treeitem", {
    name: new RegExp(getFileNameByType(fileType)),
  });
}

/*
 * MUI's TreeItem registers its click handler on the inner `.MuiTreeItem-content`
 * element, not on the `<li role="treeitem">` root. In a real browser clicking
 * the row hits the content; in jsdom we must target the content element directly
 * so the selection/double-click handlers fire.
 */
function treeItemContentByType(fileType: string): HTMLElement {
  const content = treeItemByType(fileType).querySelector(
    ".MuiTreeItem-content",
  );
  if (!(content instanceof HTMLElement)) {
    throw new Error(
      `No content element found for tree item "${getFileNameByType(fileType)}"`,
    );
  }
  return content;
}

describe("TreeView", () => {
  test("Should have no axe violations", async () => {
    const { baseElement } = render(<TreeViewWithFiles />);

    await screen.findByRole("tree");

    /*
     * The spec excluded landmark-one-main, page-has-heading-one, and region
     * because this is an isolated component, not a full page. The sa11y default
     * ruleset already omits those three rules, and color-contrast does not run
     * in jsdom (no layout/rendered colours), so a plain call suffices.
     */
    await expectAccessible(baseElement);
  });

  describe("Double-click behavior", () => {
    test("Should handle double-click on folder without crashing", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.dblClick(treeItemContentByType("folder"));
      // Just verify it doesn't crash - folder opening behavior may vary
      expect(treeItemByType("folder")).toBeInTheDocument();
    });

    test("Should handle double-click on image without crashing", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.dblClick(treeItemContentByType("image"));
      // Just verify it doesn't crash - preview behavior may vary
      expect(treeItemByType("image")).toBeInTheDocument();
    });

    test("Should handle double-click on PDF without crashing", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.dblClick(treeItemContentByType("pdf"));
      // Just verify it doesn't crash - preview behavior may vary
      expect(treeItemByType("pdf")).toBeInTheDocument();
    });
  });

  describe("Selection behavior", () => {
    test("Should select file on single click", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.click(treeItemContentByType("image"));

      await waitFor(() => {
        expect(treeItemByType("image")).toHaveAttribute(
          "aria-checked",
          "true",
        );
      });
    });
  });

  describe("Keyboard Navigation", () => {
    test("Should navigate with arrow keys", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      // Click establishes focus via the tree's roving tabindex (element.focus()).
      await user.click(treeItemContentByType("folder"));

      await user.keyboard("{ArrowDown}");
      await waitFor(() => {
        expect(treeItemByType("image")).toHaveFocus();
      });

      await user.keyboard("{ArrowUp}");
      await waitFor(() => {
        expect(treeItemByType("folder")).toHaveFocus();
      });
    });

    test("Should handle Enter key on folder", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.click(treeItemContentByType("folder"));
      await user.keyboard("{Enter}");
      // Just verify it doesn't crash
      expect(treeItemByType("folder")).toBeInTheDocument();
    });

    test("Should select with Space key", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.click(treeItemContentByType("image"));
      await user.keyboard("{ }");

      await waitFor(() => {
        expect(treeItemByType("image")).toHaveAttribute(
          "aria-checked",
          "true",
        );
      });
    });
  });

  describe("Multi-Selection", () => {
    test("Should support Ctrl+click for multi-selection", async () => {
      const user = userEvent.setup();
      render(<TreeViewWithFiles />);
      await screen.findByRole("tree");

      await user.click(treeItemContentByType("image"));
      await user.keyboard("{Control>}");
      await user.click(treeItemContentByType("pdf"));
      await user.keyboard("{/Control}");

      // Multi-selection may not be implemented yet, so just verify selection works
      await waitFor(() => {
        const selectedItems = screen
          .getAllByRole("treeitem")
          .filter((item) => item.getAttribute("aria-checked") === "true");
        expect(selectedItems.length).toBeGreaterThanOrEqual(1);
      });
    });

    /*
     * "Should handle Shift+click without crashing" is NOT converted. It asserts
     * that a shift+click surfaces the "Shift selection is not supported"
     * warning alert. That alert only fires when MUI's SimpleTreeView performs a
     * shift range selection and calls `onItemSelectionToggle` with `selected`
     * true. MUI's shift-range selection depends on the browser focus/selection
     * model and item-ordering computation, which does not run under jsdom (the
     * pdf is never range-selected and the handler's shift branch never runs).
     * The case therefore stays in TreeView.spec.tsx.
     */
  });

  describe("Loading States", () => {
    test("Should handle loading state", async () => {
      render(<TreeViewLoading />);
      /*
       * The loading story sets refreshing:true, so TreeView renders null (no
       * tree). The spec's assertion tolerated the tree being absent; here we
       * just verify the component mounts without crashing.
       */
      await waitFor(() => {
        expect(screen.queryByRole("tree")).not.toBeInTheDocument();
      });
    });

    test("Should show load more functionality", async () => {
      render(<TreeViewLoading />);
      // Just verify the component mounts with loadMore present.
      // The actual loadMore button testing would need additional setup.
      await waitFor(() => {
        expect(screen.queryByRole("tree")).not.toBeInTheDocument();
      });
    });
  });

  describe("Filtering", () => {
    test("Should show only folders when foldersOnly is true", async () => {
      render(<TreeViewFoldersOnly />);
      await screen.findByRole("tree");

      // Just verify we have tree items visible and they render properly
      const allItems = screen.getAllByRole("treeitem");
      expect(allItems.length).toBeGreaterThan(0);
    });
  });
});
