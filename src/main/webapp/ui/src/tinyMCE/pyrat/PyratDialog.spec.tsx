import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { suppressFireAndForget404, worker } from "@/__tests__/browserSetup";
import { type AnimalsRequest, pyratHandlers } from "./mocks/pyratMocks";
import { PyratDialogStory } from "./PyratDialog.story";
import { PyratDialogPage } from "./pageObjects/PyratDialogPage";

const pageObj = new PyratDialogPage();

/** Every /apps/pyrat/animals request, in order, recorded by the mock. */
let requests: Array<AnimalsRequest>;
let cleanup404: () => void;

beforeEach(() => {
  requests = [];
  worker.use(...pyratHandlers({ requests }));
  // The dialog fires version/locations/animals on mount; a late one landing
  // after teardown would otherwise surface as an unhandled 404 rejection.
  cleanup404 = suppressFireAndForget404(["/apps/pyrat"]);
});

afterEach(() => {
  cleanup404();
  cleanup();
});

/**
 * Regression coverage for RSDEV-1153: PyRAT pagination lagged one step behind
 * because the page/page-size handlers fetched imperatively with stale state.
 * The listing now refetches off the memoised query string, so each pagination
 * change produces exactly one request carrying the new values.
 */
describe("PyratDialog pagination", () => {
  test("loads the first page with the default page size", async () => {
    render(<PyratDialogStory />);

    await expect.poll(() => pageObj.dataRowCount()).toBe(10);
    expect(requests.at(-1)).toEqual({ l: 10, o: 0 });
  });

  test("changing the page size refetches with the new size and shows that many rows", async () => {
    render(<PyratDialogStory />);
    await expect.poll(() => pageObj.dataRowCount()).toBe(10);
    const before = requests.length;

    await pageObj.selectRowsPerPage(25);

    await expect.poll(() => pageObj.dataRowCount()).toBe(25);
    expect(requests.length).toBe(before + 1);
    expect(requests.at(-1)).toEqual({ l: 25, o: 0 });
  });

  test("changing the page requests the matching offset", async () => {
    render(<PyratDialogStory />);
    await expect.poll(() => pageObj.dataRowCount()).toBe(10);
    const before = requests.length;

    await pageObj.goToNextPage();

    await expect.poll(() => requests.length).toBe(before + 1);
    expect(requests.at(-1)).toEqual({ l: 10, o: 10 });
  });

  test("changing the page size while on a later page resets to the first page in a single request", async () => {
    render(<PyratDialogStory />);
    await expect.poll(() => pageObj.dataRowCount()).toBe(10);

    await pageObj.goToNextPage();
    await expect.poll(() => requests.at(-1)?.o).toBe(10);
    const before = requests.length;

    await pageObj.selectRowsPerPage(25);

    await expect.poll(() => pageObj.dataRowCount()).toBe(25);
    // Exactly one request, at offset 0: no stale-offset fetch, no double fetch.
    expect(requests.length).toBe(before + 1);
    expect(requests.at(-1)).toEqual({ l: 25, o: 0 });
  });

  test("changing the sort while on a later page resets to the first page in a single request", async () => {
    render(<PyratDialogStory />);
    await expect.poll(() => pageObj.dataRowCount()).toBe(10);
    // Ensure the (async-loaded) column labels have rendered before we click one.
    await expect.element(pageObj.columnHeaderLabel("Sex")).toBeVisible();

    await pageObj.goToNextPage();
    await expect.poll(() => requests.at(-1)?.o).toBe(10);
    const before = requests.length;

    await pageObj.sortByColumn("Sex");

    // A double-fire would push the count to before + 2, so this poll only
    // settles when the fix produced a single request.
    await expect.poll(() => requests.length).toBe(before + 1);
    expect(requests.at(-1)?.o).toBe(0);
  });
});
