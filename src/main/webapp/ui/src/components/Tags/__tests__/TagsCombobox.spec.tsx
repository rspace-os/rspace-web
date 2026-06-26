import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import AddTag from "../AddTag";
import { makeTags, ontologyTagsHandler } from "./mocks/tagsComboboxMocks";
import { TagsComboboxPage } from "./pageObjects/TagsComboboxPage";

const pageObj = new TagsComboboxPage();
let requestedPositions: Array<number>;

beforeEach(() => {
  requestedPositions = [];
  // 50 tags over a 40-per-page window: page 0 has 40 (no end signal), page 1
  // has the last 10. With LOAD_MORE_THRESHOLD=15 the first render does not
  // auto-load page 1, so scrolling is what triggers it.
  worker.use(ontologyTagsHandler({ allTags: makeTags(50), pageSize: 40, requestedPositions }));
});

afterEach(() => {
  cleanup();
});

describe("TagsCombobox (ontologies, paginated)", () => {
  test("renders virtualised tag options when opened", async () => {
    render(<AddTag onSelection={vi.fn()} value={[]} enforceOntologies={false} />);

    await pageObj.open();

    await expect.element(pageObj.option("tag-000")).toBeVisible();
    await expect.element(pageObj.option("tag-005")).toBeVisible();
  });

  test("filters server-side as the user types", async () => {
    render(<AddTag onSelection={vi.fn()} value={[]} enforceOntologies={false} />);
    await pageObj.open();
    await expect.element(pageObj.option("tag-000")).toBeVisible();

    pageObj.filter("tag-04");

    // The matching page (tag-040..tag-049) is fetched and shown; the previously
    // visible non-matching tag-000 is gone.
    await expect.element(pageObj.option("tag-040")).toBeVisible();
    await expect.element(pageObj.option("tag-000")).not.toBeInTheDocument();
  });

  test("loads the next page when scrolled near the end (List.onRowsRendered)", async () => {
    render(<AddTag onSelection={vi.fn()} value={[]} enforceOntologies={false} />);
    await pageObj.open();
    await expect.element(pageObj.option("tag-000")).toBeVisible();

    // The first render shows page 0 only; page 1 has not been requested yet.
    expect(requestedPositions).not.toContain(1);

    await pageObj.scrollListToBottom();

    // Scrolling near the end triggers a fetch for pos=1 (the migration-critical
    // behaviour), and a page-1 tag near the scrolled position becomes visible.
    await expect.poll(() => requestedPositions, { timeout: 10000 }).toContain(1);
    await expect.element(pageObj.option("tag-040")).toBeVisible();
  });

  test("selecting an option calls onSelection with the chosen tag", async () => {
    const onSelection = vi.fn();
    render(<AddTag onSelection={onSelection} value={[]} enforceOntologies={false} />);
    await pageObj.open();
    await expect.element(pageObj.option("tag-003")).toBeVisible();

    await pageObj.selectOption("tag-003");

    await expect.poll(() => onSelection.mock.calls.length).toBeGreaterThan(0);
    expect(onSelection).toHaveBeenCalledWith(expect.objectContaining({ value: "tag-003" }));
  });
});
