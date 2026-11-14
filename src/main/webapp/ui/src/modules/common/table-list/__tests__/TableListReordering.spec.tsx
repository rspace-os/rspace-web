import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { commands, page } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { TableListReorderingPage } from "./pageObjects/TableListReorderingPage";
import { reorderingStorageKey, reorderingWhere, TableListReorderingStory } from "./TableListReordering.story";

declare module "vitest/browser" {
  interface BrowserCommands {
    resizeTableColumn(label: string, delta: number): Promise<boolean>;
  }
}

const reorderedFilters = [...Array.from({ length: 11 }, (_, index) => `rule-${index + 2}`), "rule-1"].join(",");
const initialFilters = Array.from({ length: 12 }, (_, index) => `rule-${index + 1}`).join(",");

function storedView(): { columns: string | null; sort: string | null; where: string | null } | null {
  const value = localStorage.getItem(reorderingStorageKey);
  return value ? JSON.parse(value) : null;
}

async function renderStory(persist = false): Promise<TableListReorderingPage> {
  if (persist) {
    const search = new URLSearchParams({ "reordering-browser.where": reorderingWhere });
    window.history.replaceState({}, "", `/?${search}`);
    localStorage.removeItem(reorderingStorageKey);
  }
  render(<TableListReorderingStory persist={persist} />);
  const table = new TableListReorderingPage();
  await expect.element(table.filterState).toHaveTextContent(initialFilters);
  if (persist) await expect.poll(() => storedView()?.where).toBe(reorderingWhere);
  await table.resetCommitCounts();
  await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
  return table;
}

afterEach(() => {
  cleanup();
  window.history.replaceState({}, "", "/");
});

describe("TableList drag and drop", () => {
  test("previews and commits a pointer column reorder once", async () => {
    const table = await renderStory(true);
    await table.openColumns();

    const title = table.columnHandle("Title");
    const score = table.columnHandle("Score");
    const titleWidthBefore = table.itemWidth(title);
    const scoreBefore = table.itemLeft(score);
    await table.startMouseDrag(title);
    await table.moveMouseTo(score);

    await expect.poll(() => table.itemLeft(score)).toBeLessThan(scoreBefore);
    await expect.poll(() => table.itemWidth(title)).toBe(titleWidthBefore);
    await expect.element(table.columnState).toHaveTextContent("title,owner,score");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");

    await table.finishMouseDrag();

    await expect.element(table.columnState).toHaveTextContent("owner,score,title");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,1");
    await expect
      .poll(() => JSON.parse(storedView()?.columns ?? "null"))
      .toEqual({ fields: ["owner", "score", "title"] });
  });

  test("moves columns between shown and hidden sections without preview commits", async () => {
    const table = await renderStory();
    await table.openColumns();

    await table.startMouseDrag(table.columnHandle("Title"));
    await table.moveMouseTo(table.columnHandle("Enabled"));
    await expect.element(table.hiddenColumnHandle("Title")).toBeInTheDocument();
    await expect.element(table.columnState).toHaveTextContent("title,owner,score");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
    await table.finishMouseDrag();

    await expect.poll(() => table.text(table.columnState)).toBe("owner,score");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,1");

    await table.startMouseDrag(table.columnHandle("Enabled"));
    await table.moveMouseTo(table.columnHandle("Owner"));
    await expect.element(table.shownColumnHandle("Enabled")).toBeInTheDocument();
    await expect.element(table.commitCounts).toHaveTextContent("0,0,1");
    await table.finishMouseDrag();

    await expect.poll(() => table.text(table.columnState)).toContain("enabled");
    await expect.poll(() => table.text(table.columnState)).not.toContain("title");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,2");

    await table.resetCommitCounts();
    const stateBeforeHiddenReorder = table.text(table.columnState);
    await table.startMouseDrag(table.hiddenColumnHandle("Modified"));
    await table.moveMouseTo(table.hiddenColumnHandle("Title"));
    await table.finishMouseDrag();

    await expect.poll(() => table.text(table.columnState)).toBe(stateBeforeHiddenReorder);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
  });

  test("moves a column back across sections during the same pointer drag", async () => {
    const table = await renderStory();
    await table.openColumns();

    await table.startMouseDrag(table.hiddenColumnHandle("Enabled"));
    await expect.poll(() => table.columnDragOverlayText()).toBe("Enabled");
    await table.moveMouseToColumnSectionEdge("shown");
    await expect.element(table.shownColumnHandle("Enabled")).toBeInTheDocument();

    await table.moveMouseToColumnSectionEdge("hidden");
    await expect.element(table.hiddenColumnHandle("Enabled")).toBeInTheDocument();
    await table.finishMouseDrag();

    await expect.element(table.columnState).toHaveTextContent("title,owner,score");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
  });

  test("supports keyboard drop, keyboard cancel, direction changes, and removal", async () => {
    const table = await renderStory();
    await table.openSorting();

    await table.keyboardMoveDown(table.sortingHandle(1));

    await expect.element(table.sortingState).toHaveTextContent("owner:asc,title:asc,score:desc");
    await expect.element(table.commitCounts).toHaveTextContent("0,1,0");

    await table.resetCommitCounts();
    const beforeCancel = table.text(table.sortingState);
    await table.keyboardCancelMove(table.sortingHandle(1));

    await expect.element(table.sortingState).toHaveTextContent(beforeCancel);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");

    await table.selectSortingDirection("Owner", "desc");
    await table.removeSorting("Score");

    await expect.element(table.sortingState).toHaveTextContent("owner:desc,title:asc");
    await expect.element(table.commitCounts).toHaveTextContent("0,2,0");
  });

  test("supports delayed touch dragging", async () => {
    const table = await renderStory();
    await table.openSorting();

    const first = table.sortingHandle(1);
    const third = table.sortingHandle(3);
    const thirdBefore = table.itemTop(third);
    table.startTouchDrag(first);
    await expect.poll(() => table.isDragging(first)).toBe(true);
    await table.moveTouchTo(table.sortingHandle(2));
    await table.moveTouchTo(third);

    await expect.poll(() => table.itemTop(third)).toBeLessThan(thirdBefore);

    await expect.element(table.sortingState).toHaveTextContent("title:asc,owner:asc,score:desc");
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
    await table.finishTouchDrag();

    await expect.poll(() => table.text(table.sortingState)).toBe("owner:asc,score:desc,title:asc");
    await expect.element(table.commitCounts).toHaveTextContent("0,1,0");
  });

  test("leaves sorting unchanged after an ordinary touch tap", async () => {
    const table = await renderStory();
    await table.openSorting();

    const stateAfterDrop = table.text(table.sortingState);
    await table.tapDragHandle(table.sortingHandle(1));

    await expect.poll(() => table.text(table.sortingState)).toBe(stateAfterDrop);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
  });

  test("auto-scrolls filters during a pointer drag and persists only after Apply", async () => {
    const table = await renderStory();
    await table.openFilters();

    await table.startMouseDrag(table.filterHandle(1));
    await table.moveMouseNearFilterBottom();

    await expect.poll(() => table.filterScrollTop(), { timeout: 3000 }).toBeGreaterThan(0);
    await expect.element(table.filterState).toHaveTextContent(initialFilters);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");

    // Keep the pointer at the edge until auto-scroll previews a drop after the final row.
    await expect.poll(() => table.filterPreviewOffset(12), { timeout: 12000 }).toBeLessThan(0);
    await table.finishMouseDrag();

    await expect.poll(() => table.filterDraftOrder()).toBe(reorderedFilters);

    await expect.element(table.filterState).toHaveTextContent(initialFilters);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
    await table.applyFilters();

    await expect.poll(() => table.text(table.filterState)).toBe(reorderedFilters);
    await expect.element(table.commitCounts).toHaveTextContent("1,0,0");
  });

  test("cancels a pointer drag without changing or persisting the layout", async () => {
    const table = await renderStory(true);
    await table.openColumns();

    const stateBefore = table.text(table.columnState);
    const storageBefore = localStorage.getItem(reorderingStorageKey);
    await table.startMouseDrag(table.columnHandle("Title"));
    await table.moveMouseTo(table.columnHandle("Score"));
    await table.cancelMouseDrag();

    await expect.element(table.columnState).toHaveTextContent(stateBefore);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
    expect(localStorage.getItem(reorderingStorageKey)).toBe(storageBefore);
  });
});

describe("TableList column resizing", () => {
  beforeEach(async () => {
    await page.viewport(1280, 800);
  });
  test("keeps sticky Actions at the container edge when the fill column reaches its maximum", async () => {
    render(<TableListReorderingStory intrinsicWidths maxWidth={240} />);
    const table = new TableListReorderingPage();
    await table.resizeColumnWithKeyboard("Owner", "{ArrowRight}");
    await expectNoAxeViolations();
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(240, 0);
    const actions = page.getByRole("columnheader", { name: "Actions", exact: true });
    expect(getComputedStyle(actions.element()).position).toBe("sticky");
    await expect
      .poll(() => {
        const container = page.getByRole("table").element().parentElement;
        if (!container) throw new Error("Table container unavailable");
        return actions.element().getBoundingClientRect().right - container.getBoundingClientRect().right;
      })
      .toBeCloseTo(0, 0);
  });

  test("refits the fill after clicking its handle without movement", async () => {
    render(<TableListReorderingStory intrinsicWidths />);
    const table = new TableListReorderingPage();
    await table.resizeHandle("Title").click();
    const before = table.columnWidth("Title");
    await page.viewport(800, 800);
    await expect.poll(() => table.columnWidth("Title")).toBeLessThan(before);
  });

  test("cancels capture when a rendered header is missing", async () => {
    render(<TableListReorderingStory intrinsicWidths />);
    const table = new TableListReorderingPage();
    const owner = page.getByRole("columnheader", { name: "Owner", exact: true }).element();
    const id = owner.getAttribute("data-column-id");
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    try {
      owner.removeAttribute("data-column-id");
      const before = table.columnWidth("Title");
      await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
      expect(warn).toHaveBeenCalledWith("Missing rendered header for column owner; cancelling column resize");
      expect(table.columnWidth("Title")).toBe(before);
      expect(page.getByRole("table").element()).toHaveClass("table-auto");
    } finally {
      if (id) owner.setAttribute("data-column-id", id);
      warn.mockRestore();
    }
    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
    expect(page.getByRole("table").element()).toHaveClass("table-fixed");
  });

  test("keeps a user-shrunk fill width and starts the next resize there", async () => {
    render(<TableListReorderingStory intrinsicWidths />);
    const table = new TableListReorderingPage();
    const initial = table.columnWidth("Title");
    await table.resizeColumnWithKeyboard("Title", "{ArrowLeft}");
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(initial - 16, 0);
    await table.resizeColumnWithKeyboard("Title", "{ArrowLeft}".repeat(20));
    const element = page.getByRole("table").element();
    expect(element.getBoundingClientRect().width).toBe(element.parentElement?.clientWidth);
    const before = table.columnWidth("Title");
    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(before + 16, 0);
  });

  test("refits an untouched fill on container shrink but preserves a user-sized fill", async () => {
    render(<TableListReorderingStory intrinsicWidths contentWidths />);
    const table = new TableListReorderingPage();
    await table.resizeColumnWithKeyboard("Owner", "{ArrowRight}");
    const before = table.columnWidth("Title");
    await page.viewport(800, 800);
    await expect.poll(() => table.columnWidth("Title")).toBeLessThan(before);
    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
    const chosen = table.columnWidth("Title");
    await page.viewport(700, 800);
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(chosen, 0);
  });

  test.each(["keyboard", "touch"] as const)("captures fitted widths on a fresh %s resize", async (input) => {
    render(<TableListReorderingStory intrinsicWidths />);
    const table = new TableListReorderingPage();
    const before = table.columnWidth("Title");
    if (input === "keyboard") await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
    else await table.resizeColumnWithTouch("Title", 16);
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(before + 16, 0);
  });

  test("native pointer resize focuses the handle and preserves widths after hiding the fill column", async () => {
    render(<TableListReorderingStory intrinsicWidths />);
    const table = new TableListReorderingPage();
    const before = table.columnWidth("Title");
    expect(await commands.resizeTableColumn("Title", 64)).toBe(true);
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(before + 64, 0);
    await table.openColumns();
    await page.getByRole("button", { name: "Hide Title column", exact: true }).click();
    await page.getByRole("button", { name: /^Columns/ }).click();
    const actions = page.getByRole("columnheader", { name: "Actions", exact: true });
    const tableElement = page.getByRole("table").element();
    expect(actions.element().getBoundingClientRect().right).toBeCloseTo(
      tableElement.parentElement?.getBoundingClientRect().right ?? 0,
      0,
    );
    expect(page.getByRole("columnheader").all()).toHaveLength(3);

    const ownerBefore = table.columnWidth("Owner");
    await commands.resizeTableColumn("Owner", 32);
    await expect.poll(() => table.columnWidth("Owner")).toBeCloseTo(ownerBefore + 32, 0);
    await table.resizeColumnWithKeyboard("Owner", "{ArrowLeft}{ArrowLeft}{ArrowLeft}");
    const element = page.getByRole("table").element();
    expect(element.getBoundingClientRect().width).toBeGreaterThanOrEqual(element.parentElement?.clientWidth ?? 0);
    await table.resizeColumnWithKeyboard("Owner", "{Enter}");
    expect(element).toHaveClass("table-auto");
    const resetWidth = table.columnWidth("Owner");
    await table.resizeColumnWithKeyboard("Owner", "{ArrowRight}");
    await expect.poll(() => table.columnWidth("Owner")).toBeCloseTo(resetWidth + 16, 0);
  });

  test("honours rendered widths when resizing fill and content columns", async () => {
    render(<TableListReorderingStory intrinsicWidths contentWidths />);
    const table = new TableListReorderingPage();
    const titleBefore = table.columnWidth("Title");
    await table.startMouseColumnResize("Title");
    await table.moveMouseColumnResize(64);
    await table.finishMouseColumnResize();
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(titleBefore + 64, 0);

    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}{ArrowRight}{ArrowLeft}");
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(titleBefore + 80, 0);
    await table.resizeColumnWithTouch("Owner", -300);
    await expect.poll(() => table.columnWidth("Owner")).toBeCloseTo(120, 0);
    const actionsBefore = table.columnWidth("Actions");
    await table.resizeColumnWithKeyboard("Actions", "{ArrowRight}");
    await expect.poll(() => table.columnWidth("Actions")).toBeCloseTo(actionsBefore + 16, 0);
    const element = page.getByRole("table").element();
    expect(element.getBoundingClientRect().width).toBeGreaterThanOrEqual(element.parentElement?.clientWidth ?? 0);
    await table.resizeColumnWithKeyboard("Title", "{Enter}");
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(titleBefore, 0);
    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}{ }");
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(titleBefore, 0);
    await table.resizeColumnWithKeyboard("Title", "{ArrowRight}");
    await table.resizeHandle("Title").dblClick();
    await expect.poll(() => table.columnWidth("Title")).toBeCloseTo(titleBefore, 0);
  });

  test("resizes field and React columns without persisting their widths", async () => {
    const table = await renderStory(true);
    const searchBefore = window.location.search;
    const storageBefore = localStorage.getItem(reorderingStorageKey);

    const titleBefore = table.columnWidth("Title");
    await table.startMouseColumnResize("Title");
    await table.moveMouseColumnResize(64);
    await expect.poll(() => table.columnWidth("Title")).toBe(titleBefore);
    await expect.poll(() => table.resizeIndicatorTransform("Title")).toBe("translateX(64px)");
    await table.finishMouseColumnResize();
    await expect.poll(() => table.columnWidth("Title")).toBeGreaterThan(titleBefore + 48);
    await expect.poll(() => table.resizeIndicatorTransform("Title")).toBe("translateX(0px)");

    await table.resizeColumnWithTouch("Owner", -300);
    await expect.poll(() => table.columnWidth("Owner")).toBeGreaterThanOrEqual(119);
    await expect.poll(() => table.columnWidth("Owner")).toBeLessThan(121);

    await table.resizeColumnWithKeyboard("Actions", "{ArrowLeft}{ArrowLeft}{ArrowLeft}");
    await expect.poll(() => table.columnWidth("Actions")).toBeGreaterThanOrEqual(95);
    await expect.poll(() => table.columnWidth("Actions")).toBeLessThan(97);

    await table.resizeColumnWithKeyboard("Actions", "{Enter}");
    await expect.poll(() => table.columnWidth("Actions")).toBeGreaterThanOrEqual(119);

    expect(window.location.search).toBe(searchBefore);
    expect(localStorage.getItem(reorderingStorageKey)).toBe(storageBefore);
  });
});
