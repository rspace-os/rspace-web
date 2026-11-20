import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { RequestUserChipPage } from "./pageObjects/RequestUserChipPage";
import { RequestUserChipInTable } from "./RequestUserChip.story";

const pageObj = new RequestUserChipPage();

function isTruncated(element: Element): boolean {
  return element.scrollWidth > element.clientWidth;
}

afterEach(() => {
  cleanup();
});

describe("RequestUserChip", () => {
  test("truncates a long name to the column's available width instead of overflowing the table", async () => {
    render(<RequestUserChipInTable width={240} />);
    await expect.element(pageObj.chip).toBeVisible();

    await expect.poll(() => pageObj.table.element().scrollWidth).toBeLessThanOrEqual(240);
    await expect.poll(() => isTruncated(pageObj.chipLabel.element())).toBe(true);
  });

  test("shows the full name when the column has room for it", async () => {
    render(<RequestUserChipInTable width={800} />);
    await expect.element(pageObj.chip).toBeVisible();

    await expect.poll(() => isTruncated(pageObj.chipLabel.element())).toBe(false);
  });
});
