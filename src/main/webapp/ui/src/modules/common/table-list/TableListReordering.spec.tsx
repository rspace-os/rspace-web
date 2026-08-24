import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { TableListReorderingPage } from "./pageObjects/TableListReorderingPage";
import { reorderingStorageKey, reorderingWhere, TableListReorderingStory } from "./TableListReordering.story";

const initialFilters = Array.from({ length: 12 }, (_, index) => `rule-${index + 1}`).join(",");

function storedView(): { columns: string | null; sort: string | null; where: string | null } | null {
  const value = localStorage.getItem(reorderingStorageKey);
  return value ? JSON.parse(value) : null;
}

async function renderStory(persist = false): Promise<TableListReorderingPage> {
  render(<TableListReorderingStory persist={persist} />);
  const table = new TableListReorderingPage();
  await expect.element(table.filterState).toHaveTextContent(initialFilters);
  if (persist) await expect.poll(() => storedView()?.where).toBe(reorderingWhere);
  await table.resetCommitCounts();
  await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
  return table;
}

beforeEach(() => {
  const search = new URLSearchParams({ "reordering-browser.where": reorderingWhere });
  window.history.replaceState({}, "", `/?${search}`);
  localStorage.removeItem(reorderingStorageKey);
});

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

    await expect.poll(() => table.filterScrollTop()).toBeGreaterThan(0);
    await expect.element(table.filterState).toHaveTextContent(initialFilters);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");

    await table.moveMouseTo(table.filterHandle(12), 12);
    await table.moveMouseTo(table.filterHandle(12), 16);
    await table.finishMouseDrag();

    await expect
      .poll(() => table.filterDraftOrder())
      .toBe([...Array.from({ length: 11 }, (_, index) => `rule-${index + 2}`), "rule-1"].join(","));

    await expect.element(table.filterState).toHaveTextContent(initialFilters);
    await expect.element(table.commitCounts).toHaveTextContent("0,0,0");
    await table.applyFilters();

    const reorderedFilters = [...Array.from({ length: 11 }, (_, index) => `rule-${index + 2}`), "rule-1"].join(",");
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
