import { cleanup, render } from "@testing-library/react";
import type { Editor } from "tinymce";
import { afterEach, expect, test } from "vitest";
import StyledTinyMceEditor from "./StyledTinyMceEditor";

afterEach(cleanup);

test("loads normal and italic Roboto inside the editor iframe", async () => {
  let editor: Editor | undefined;
  render(
    <StyledTinyMceEditor
      initialValue="<p>Normal text and <em>italic text</em></p>"
      init={{ menubar: false, toolbar: false }}
      onInit={(_event, instance) => {
        editor = instance;
      }}
    />,
  );

  await expect.poll(() => editor?.initialized).toBe(true);
  if (!editor) throw new Error("Editor did not initialize");
  const doc = editor.getDoc();
  expect(doc.defaultView?.getComputedStyle(editor.getBody()).fontFamily).toContain("Roboto Variable");
  // A matching family name alone can pass while the iframe uses a system font.
  const normal = await doc.fonts.load('400 16px "Roboto Variable"', "Normal text");
  const italic = await doc.fonts.load('italic 400 16px "Roboto Variable"', "italic text");
  expect(normal.length).toBeGreaterThan(0);
  expect(italic.length).toBeGreaterThan(0);
  expect(normal.every((font) => font.status === "loaded" && font.style === "normal")).toBe(true);
  expect(italic.every((font) => font.status === "loaded" && font.style === "italic")).toBe(true);
});
