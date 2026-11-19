import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { userEvent } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { TextDefaultEditorPage } from "./pageObjects/TextDefaultEditorPage";
import { TextDefaultEditorStory } from "./TextDefaultEditor.story";

const editor = new TextDefaultEditorPage();

afterEach(() => {
  cleanup();
});

describe("TextDefaultEditor", () => {
  test("edits the default text in TinyMCE and keeps it on Done", async () => {
    const onChange = vi.fn();
    render(<TextDefaultEditorStory onChange={onChange} />);

    await editor.editButton.click();
    await expect.element(editor.boldButton).toBeVisible();
    await expect.element(editor.editorBody).toBeVisible();
    await userEvent.type(editor.editorBody, "Describe the risks.");
    await editor.done();

    await expect.element(editor.dialog).not.toBeInTheDocument();
    await expect.element(editor.preview).toHaveTextContent("Describe the risks.");
    expect(onChange).toHaveBeenLastCalledWith("<p>Describe the risks.</p>");
  });

  test("leaves the default unchanged on Cancel", async () => {
    const onChange = vi.fn();
    render(<TextDefaultEditorStory initial="<p>Keep me</p>" onChange={onChange} />);

    await editor.editButton.click();
    await expect.element(editor.boldButton).toBeVisible();
    await expect.element(editor.editorBody).toBeVisible();
    await userEvent.type(editor.editorBody, " and more");
    await editor.cancel();

    await expect.element(editor.preview).toHaveTextContent("Keep me");
    expect(onChange).not.toHaveBeenCalled();
  });

  test("is accessible with the editor open", async () => {
    render(<TextDefaultEditorStory initial="<p>Keep me</p>" />);
    await editor.editButton.click();
    await expect.element(editor.boldButton).toBeVisible();
    // MUI fades the dialog's container in; axe measures contrast mid-fade otherwise
    await expect.poll(() => getComputedStyle(editor.dialog.element().parentElement ?? document.body).opacity).toBe("1");
    await expectNoAxeViolations();
  });
});
