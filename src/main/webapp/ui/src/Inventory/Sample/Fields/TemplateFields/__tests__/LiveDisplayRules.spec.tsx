import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { LiveDisplayRulesStory } from "./LiveDisplayRules.story";
import { LiveDisplayRulesPage } from "./pageObjects/LiveDisplayRulesPage";

const pageObj = new LiveDisplayRulesPage();

afterEach(() => {
  cleanup();
});

/** Longer than the story's pause, for the steps that wait for the fields to move. */
const AFTER_PAUSE = { timeout: 5000 };

describe("Template fields shown by a number field", () => {
  test("keep a field that focus entered before the update shown until focus leaves it", async () => {
    render(<LiveDisplayRulesStory onSave={() => {}} />);
    await expect.element(pageObj.permit).toBeVisible();

    await pageObj.typeWeight("1");
    // before the pause ends, Tab moves into the field the new weight hides
    await pageObj.pressTab();
    await expect.element(pageObj.permit).toHaveFocus();

    // the update has landed once the field the new weight shows appears
    await expect.element(pageObj.lightNote, AFTER_PAUSE).toBeVisible();
    await expect.element(pageObj.status).toMatchTextContent("Light goods note");
    await expect.element(pageObj.permit).toBeVisible();
    await expect.element(pageObj.permit).toHaveFocus();

    await pageObj.pressTab();
    await expect.element(pageObj.lightNote).toHaveFocus();
    await expect.element(pageObj.permit).not.toBeInTheDocument();
    await expect.element(pageObj.status).toMatchTextContent("Heavy goods permit");
  });

  test("submit with the typed value when Enter follows the typing at once", async () => {
    // whether the permit, which the typed weight hides, was still on screen at each save
    const permitShownAtSave: Array<boolean> = [];
    render(<LiveDisplayRulesStory onSave={() => permitShownAtSave.push(pageObj.permit.query() !== null)} />);
    await expect.element(pageObj.permit).toBeVisible();

    // the empty mandatory permit blocks saving while it is shown
    await pageObj.pressEnterInWeight();
    expect(permitShownAtSave).toEqual([]);

    await pageObj.typeWeight("1");
    await pageObj.pressEnter();

    // saving read the typed weight while the permit was still waiting to leave the screen
    await expect.poll(() => permitShownAtSave).toEqual([true]);
    await expect.element(pageObj.permit, AFTER_PAUSE).not.toBeInTheDocument();
  });
});
