import { cleanup, render } from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import RsSet from "@/util/set";
import TagsCombobox from "../TagsCombobox";
import { makeTags, userTagsHandler } from "./mocks/userTagsMocks";
import { UserTagsComboboxPage } from "./pageObjects/UserTagsComboboxPage";

const pageObj = new UserTagsComboboxPage();
let requestedFilters: Array<string>;

/**
 * Harness mirroring how sysadmin/users/index.tsx opens the combobox: a trigger
 * button sets `anchorEl`, which mounts the Popover-anchored TagsCombobox.
 */
function UserTagsHarness({ onSelection }: { onSelection: (tag: string) => void }) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  return (
    <>
      <button type="button" onClick={(e) => setAnchorEl(e.currentTarget)}>
        {"Open user tags"}
      </button>
      <TagsCombobox
        anchorEl={anchorEl}
        value={new RsSet<string>([])}
        onSelection={onSelection}
        onClose={() => setAnchorEl(null)}
        allowNewTags
      />
    </>
  );
}

beforeEach(() => {
  requestedFilters = [];
  worker.use(userTagsHandler({ allTags: makeTags(50), requestedFilters }));
});

afterEach(() => {
  cleanup();
});

describe("TagsCombobox (sysadmin users, loads all tags)", () => {
  test("shows matching options after typing a filter", async () => {
    render(<UserTagsHarness onSelection={vi.fn()} />);
    await pageObj.open();

    // The combobox only fetches once the filter is >= 2 chars.
    pageObj.filter("tag-00");

    await expect.element(pageObj.option("tag-000")).toBeVisible();
    await expect.element(pageObj.option("tag-005")).toBeVisible();
  });

  test("filters server-side as the user types", async () => {
    render(<UserTagsHarness onSelection={vi.fn()} />);
    await pageObj.open();

    pageObj.filter("tag-00");
    await expect.element(pageObj.option("tag-000")).toBeVisible();

    pageObj.filter("tag-04");

    // Now only tag-04x matches; a previously shown tag-000 is gone.
    await expect.element(pageObj.option("tag-040")).toBeVisible();
    await expect.element(pageObj.option("tag-000")).not.toBeInTheDocument();
  });

  test("selecting an option calls onSelection with the tag value", async () => {
    const onSelection = vi.fn();
    render(<UserTagsHarness onSelection={onSelection} />);
    await pageObj.open();

    pageObj.filter("tag-00");
    await expect.element(pageObj.option("tag-003")).toBeVisible();

    await pageObj.selectOption("tag-003");

    await expect.poll(() => onSelection.mock.calls.length).toBeGreaterThan(0);
    expect(onSelection).toHaveBeenCalledWith("tag-003");
  });
});
