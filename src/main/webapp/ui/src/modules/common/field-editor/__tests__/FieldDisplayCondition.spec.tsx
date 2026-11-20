import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import materialTheme from "@/theme";
import { FieldDisplayConditionStory, noAvailableFields } from "./FieldDisplayCondition.story";
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

/** The focus outline of a disabled item: solid, 2px, in the text colour, and not faded. */
function expectReadableFocus(item: Element): void {
  const style = getComputedStyle(item);
  expect([style.outlineStyle, style.outlineWidth, style.outlineColor, style.opacity]).toEqual([
    "solid",
    "2px",
    computedColour(materialTheme.palette.text.primary),
    "1",
  ]);
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
    // the component's own outline, in the text colour rather than the theme's faint primary
    expectReadableFocus(pageObj.notes.element());

    await pageObj.press("{Enter}");
    await pageObj.press(" ");
    await expect.element(pageObj.notes).toHaveFocus();

    await pageObj.press("{Escape}");
    await expect.element(pageObj.listbox).not.toBeInTheDocument();
    await expect.element(pageObj.field).toHaveTextContent("Is this a GMO?");
    expect(changes).toEqual([]);
  });

  test("opened from the keyboard with nothing to choose, focuses a readable 'No available fields'", async () => {
    render(<FieldDisplayConditionStory onChange={() => {}} fields={noAvailableFields} />);
    await pageObj.chooseConditional();
    pageObj.field.element().focus();
    await pageObj.press("{ArrowDown}");
    await expect.element(pageObj.noAvailableFields).toHaveFocus();
    expectReadableFocus(pageObj.noAvailableFields.element());
  });
});
