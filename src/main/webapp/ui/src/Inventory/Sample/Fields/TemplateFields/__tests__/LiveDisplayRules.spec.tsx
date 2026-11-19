import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { LiveDisplayRulesStory } from "./LiveDisplayRules.story";
import { LiveDisplayRulesPage } from "./pageObjects/LiveDisplayRulesPage";

const pageObj = new LiveDisplayRulesPage();

afterEach(() => {
  cleanup();
});

describe("Template fields shown by a number field", () => {
  test("keep a field that focus entered before the update shown until focus leaves it", async () => {
    render(<LiveDisplayRulesStory onSave={() => {}} />);
    await expect.element(pageObj.permit).toBeVisible();

    await pageObj.typeWeight("1");
    // before the pause ends, Tab moves into the field the new weight hides
    await pageObj.pressTab();
    await expect.element(pageObj.permit).toHaveFocus();

    // the update has landed once the field the new weight shows appears
    await expect.element(pageObj.lightNote).toBeVisible();
    await expect.element(pageObj.status).toMatchTextContent("Light goods note");
    await expect.element(pageObj.permit).toBeVisible();
    await expect.element(pageObj.permit).toHaveFocus();

    await pageObj.pressTab();
    await expect.element(pageObj.lightNote).toHaveFocus();
    await expect.element(pageObj.permit).not.toBeInTheDocument();
    await expect.element(pageObj.status).toMatchTextContent("Heavy goods permit");
  });

  test("submit with the typed value when Enter follows the typing at once", async () => {
    let saves = 0;
    render(<LiveDisplayRulesStory onSave={() => saves++} />);
    await expect.element(pageObj.permit).toBeVisible();

    // the empty mandatory permit blocks saving while it is shown
    await pageObj.pressEnterInWeight();
    expect(saves).toBe(0);

    await pageObj.typeWeight("1");
    await pageObj.pressEnter();

    await expect.poll(() => saves).toBe(1);
  });
});
