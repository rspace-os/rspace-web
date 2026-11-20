import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { FieldDisplayConditionStory } from "./FieldDisplayCondition.story";
import { FieldDisplayConditionPage } from "./pageObjects/FieldDisplayConditionPage";

const pageObj = new FieldDisplayConditionPage();

afterEach(() => {
  cleanup();
});

describe("The condition's Field menu", () => {
  test("reaches an unavailable field with the arrow keys, and Enter and Space do not choose it", async () => {
    const changes: Array<unknown> = [];
    render(<FieldDisplayConditionStory onChange={(rule) => changes.push(rule)} />);
    await pageObj.openMenu();
    await expect.element(pageObj.chosen).toHaveFocus();

    await pageObj.press("{ArrowDown}");
    await expect.element(pageObj.notes).toHaveFocus();
    await expect.element(pageObj.notes).toHaveAttribute("aria-disabled", "true");

    await pageObj.press("{Enter}");
    await pageObj.press(" ");
    await expect.element(pageObj.notes).toHaveFocus();

    await pageObj.press("{Escape}");
    await expect.element(pageObj.listbox).not.toBeInTheDocument();
    await expect.element(pageObj.field).toHaveTextContent("Is this a GMO?");
    expect(changes).toEqual([]);
  });
});
