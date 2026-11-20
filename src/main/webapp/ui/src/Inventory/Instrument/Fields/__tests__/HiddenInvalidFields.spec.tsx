import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { HiddenInvalidFieldsStory } from "./HiddenInvalidFields.story";
import { HiddenInvalidFieldsPage } from "./pageObjects/HiddenInvalidFieldsPage";

const pageObj = new HiddenInvalidFieldsPage();

afterEach(() => {
  cleanup();
});

describe("An instrument number field hidden while it holds text the browser cannot read", () => {
  test("keeps a number input's unreadable text, blocking Save while hidden, until it is cleared", async () => {
    render(<HiddenInvalidFieldsStory />);
    await expect.element(pageObj.save).toBeEnabled();

    await pageObj.typeHoursRun("2e");
    await expect.element(pageObj.hoursRun).toBeInvalid();
    await pageObj.answerServiced("No");

    await expect.element(pageObj.hoursRun).not.toBeInTheDocument();
    await expect.element(pageObj.save).toBeDisabled();
    await expect.element(pageObj.saveReason).toMatchTextContent('"Hours run" is hidden');
    await expect.element(pageObj.saveReason).toMatchTextContent('Change "Serviced?" to show it');

    await pageObj.answerServiced("Yes");
    await expect.element(pageObj.hoursRun).toBeInvalid();
    // the browser holds the text "2e" only in the input, which reports it as text it cannot read
    expect((pageObj.hoursRun.element() as HTMLInputElement).validity.badInput).toBe(true);
    await expect.element(pageObj.save).toBeDisabled();

    await pageObj.clearHoursRun();
    await expect.element(pageObj.hoursRun).toBeValid();
    await expect.element(pageObj.saveReason).not.toBeInTheDocument();
    await expect.element(pageObj.save).toBeEnabled();
  });
});
