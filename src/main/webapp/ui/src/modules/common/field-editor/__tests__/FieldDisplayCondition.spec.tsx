import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import materialTheme from "@/theme";
import { FieldDisplayConditionStory } from "./FieldDisplayCondition.story";
import { FieldDisplayConditionPage } from "./pageObjects/FieldDisplayConditionPage";

const pageObj = new FieldDisplayConditionPage();

/** A theme colour as the browser reports it in computed styles. */
function computedColour(colour: string): string {
  const probe = document.createElement("span");
  probe.style.color = colour;
  document.body.append(probe);
  const computed = getComputedStyle(probe).color;
  probe.remove();
  return computed;
}

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
    // MUI styles no focus on a disabled item, so the component draws its own, in the text colour
    // rather than the browser's default ring
    const focused = getComputedStyle(pageObj.notes.element());
    expect([focused.outlineStyle, focused.outlineWidth, focused.outlineColor]).toEqual([
      "solid",
      "2px",
      computedColour(materialTheme.palette.text.primary),
    ]);

    await pageObj.press("{Enter}");
    await pageObj.press(" ");
    await expect.element(pageObj.notes).toHaveFocus();

    await pageObj.press("{Escape}");
    await expect.element(pageObj.listbox).not.toBeInTheDocument();
    await expect.element(pageObj.field).toHaveTextContent("Is this a GMO?");
    expect(changes).toEqual([]);
  });
});
