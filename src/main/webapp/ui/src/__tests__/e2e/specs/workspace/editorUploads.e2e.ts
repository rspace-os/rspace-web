import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type Page } from "@playwright/test";
import type { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";

const currentDir = dirname(fileURLToPath(import.meta.url));
const IMAGE_PATH = resolve(currentDir, "../inventory/fixtures/container_preview.png");
const LOCAL_IMAGE = '<img src="file:///C:/Users/RSpace/Pictures/Photos-copy.png"/>';
const clipboardHtml = (fragment: string) =>
  `<html><body>\r\n<!--StartFragment-->${fragment}<!--EndFragment-->\r\n</body></html>`;
// The HTML Windows Photos puts on the clipboard next to the image file: a local URL the browser can't load.
const PHOTOS_HTML = clipboardHtml(LOCAL_IMAGE);

type ClipboardPayload = { html: string; text?: string; plainTextShortcut?: boolean; htmlInPasteBin?: boolean };

/**
 * Replays a keyboard paste of an image file plus the given formats. The keydown lets TinyMCE's paste
 * plugin prepare its paste bin as it does for a real Ctrl+V.
 */
async function pasteImageWith(
  field: TinyMceEditor,
  { html, text, plainTextShortcut = false, htmlInPasteBin = false }: ClipboardPayload,
) {
  const png = (await readFile(IMAGE_PATH)).toString("base64");
  await field.body.evaluate(
    (body, payload) => {
      const data = new DataTransfer();
      if (!payload.htmlInPasteBin) data.setData("text/html", payload.html);
      if (payload.text) data.setData("text/plain", payload.text);
      const bytes = Uint8Array.from(atob(payload.png), (c) => c.charCodeAt(0));
      data.items.add(new File([bytes], "Photos-copy.png", { type: "image/png" }));
      body.dispatchEvent(
        new KeyboardEvent("keydown", {
          bubbles: true,
          cancelable: true,
          key: "v",
          keyCode: 86,
          ctrlKey: true,
          metaKey: true,
          shiftKey: payload.plainTextShortcut,
        }),
      );
      const target = payload.htmlInPasteBin ? body.querySelector("#mcepastebin") : body;
      if (!target) throw new Error("TinyMCE did not create a paste bin on keydown");
      const allowDefault = target.dispatchEvent(
        new ClipboardEvent("paste", { bubbles: true, cancelable: true, clipboardData: data }),
      );
      // The browser's default paste runs after dispatch, before TinyMCE's zero-delay paste-bin read.
      if (allowDefault && payload.htmlInPasteBin) target.innerHTML = payload.html;
    },
    { html, text, plainTextShortcut, htmlInPasteBin, png },
  );
}

function countGalleryUploads(page: Page): () => number {
  let uploads = 0;
  page.on("request", (request) => {
    if (request.method() === "POST" && new URL(request.url()).pathname === "/gallery/ajax/uploadFile/") uploads++;
  });
  return () => uploads;
}

function togglePlainTextPaste(page: Page, field: TinyMceEditor): Promise<void> {
  return page.evaluate((editorId) => {
    const { tinymce } = window as unknown as {
      tinymce: { get(id: string): { execCommand(command: string): void } };
    };
    tinymce.get(editorId).execCommand("mceTogglePlainTextPaste");
  }, field.editorId);
}

test.describe("Workspace editor uploads", () => {
  test("As a user, I can upload an image from my computer into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("New List of Materials");

    await field.insertImageAttachment(IMAGE_PATH);

    await expect(field.imageElement).toHaveAttribute("alt", "image container_preview.png");

    const document = await editor.saveAndView();
    const savedField = await document.getFieldViewContent("New List of Materials");
    await expect(savedField.getByRole("img", { name: "image container_preview.png" })).toBeVisible();
  });

  test.describe("Pasting", () => {
    test.skip(
      ({ browserName }) => browserName === "firefox",
      "Firefox hides the data of script-created paste events, so the clipboard can't be replayed",
    );

    for (const { pasteMode, plainTextMode, htmlInPasteBin } of [
      { pasteMode: "normal paste" },
      { pasteMode: "paste as text", plainTextMode: true },
      { pasteMode: "HTML delivered through the paste bin", htmlInPasteBin: true },
    ]) {
      test(`As a user, I can paste an image copied from Windows Photos with ${pasteMode}`, async ({
        page,
        pageWorkspace,
      }) => {
        await pageWorkspace.open();
        const editor = await pageWorkspace.createBasicDocument();
        const field = await editor.getField("New List of Materials");
        await field.typeLines(["Before image", "After image"]);
        if (plainTextMode) await togglePlainTextPaste(page, field);
        await field.body.getByText("Before image").click();
        await field.body.press("End");
        const uploads = countGalleryUploads(page);

        const [response] = await Promise.all([
          page.waitForResponse(
            (res) => res.request().method() === "POST" && new URL(res.url()).pathname === "/gallery/ajax/uploadFile/",
          ),
          pasteImageWith(field, { html: PHOTOS_HTML, htmlInPasteBin }),
        ]);

        expect(response.ok()).toBe(true);
        const image = field.body.getByRole("img", { name: "image Photos-copy.png" });
        await expect(image).toBeVisible();
        await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
        await expect(field.imageElement).toHaveCount(1);
        await expect(field.body.locator('img[src^="file:"]')).toHaveCount(0);
        // TinyMCE's paste bin, left behind if the paste plugin doesn't finish handling the event.
        await expect(field.body.locator("#mcepastebin")).toHaveCount(0);
        const textAroundImage = await image.evaluate((img) => {
          const range = img.ownerDocument.createRange();
          range.selectNodeContents(img.ownerDocument.body);
          range.setEndBefore(img);
          return range.toString();
        });
        expect(textAroundImage).toBe("Before image");
        expect(uploads()).toBe(1);

        const document = await editor.saveAndView();
        const savedField = await document.getFieldViewContent("New List of Materials");
        await expect(savedField.getByRole("img", { name: "image Photos-copy.png" })).toBeVisible();
      });
    }

    test("As a user, I can paste the text that accompanies a local image", async ({ page, pageWorkspace }) => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("New List of Materials");
      await field.typeLines(["Before shortcut", "Before caption", "After caption"]);
      const uploads = countGalleryUploads(page);

      await field.body.getByText("Before shortcut", { exact: true }).click();
      await field.body.press("End");
      await pasteImageWith(field, { html: PHOTOS_HTML, text: " Shortcut caption", plainTextShortcut: true });
      await expect(field.body.getByText("Before shortcut Shortcut caption", { exact: true })).toBeVisible();
      await expect(field.body.locator("#mcepastebin")).toHaveCount(0);
      expect(uploads()).toBe(0);

      await togglePlainTextPaste(page, field);
      await field.body.getByText("Before caption", { exact: true }).click();
      await field.body.press("End");
      await pasteImageWith(field, { html: PHOTOS_HTML, text: "<b>x</b>" });
      await expect(field.body.getByText("Before caption<b>x</b>", { exact: true })).toBeVisible();
      await expect(field.body.getByText("After caption", { exact: true })).toBeVisible();
      await expect(field.body.locator("#mcepastebin")).toHaveCount(0);
      expect(uploads()).toBe(0);

      await togglePlainTextPaste(page, field);
      await pasteImageWith(field, { html: clipboardHtml(`<p><strong>Rich caption</strong></p>${LOCAL_IMAGE}`) });
      await expect(field.body.locator("strong")).toHaveText("Rich caption");

      await pasteImageWith(field, {
        html: clipboardHtml(`<p><strong>Paste bin caption</strong></p>${LOCAL_IMAGE}`),
        htmlInPasteBin: true,
      });
      await expect(field.body.locator("strong").filter({ hasText: "Paste bin caption" })).toBeVisible();
      await expect(field.body.locator("#mcepastebin")).toHaveCount(0);

      await expect(field.imageElement).toHaveCount(0);
      expect(uploads()).toBe(0);
    });
  });
});
