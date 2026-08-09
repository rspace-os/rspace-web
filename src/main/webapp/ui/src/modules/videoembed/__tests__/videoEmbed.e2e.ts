import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import {
  MOCK_JOVE_EMBED_BODY,
  MOCK_JOVE_ID,
  MOCK_TIB_EMBED_BODY,
  MOCK_TIB_ID,
  MOCK_YOUTUBE_EMBED_BODY,
  MOCK_YOUTUBE_ID,
} from "./mock";

test.describe("Video embed (YouTube/JoVE/TIB AV-Portal) [mock]", { tag: tags.APPS }, () => {
  test("As a user, I can embed a YouTube video into a document field", async ({ page, pageWorkspace }) => {
    await page.route(new RegExp(`^https://www\\.youtube\\.com/embed/${MOCK_YOUTUBE_ID}`), (route) =>
      route.fulfill({ contentType: "text/html", body: MOCK_YOUTUBE_EMBED_BODY }),
    );

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    await field.clickToolbarButton("Embed video");
    await docEditor.videoEmbedDialog.waitForOpen();
    await expect(docEditor.videoEmbedDialog.feedback).toHaveText("Paste a supported video URL.");

    await docEditor.videoEmbedDialog.urlInput.fill(`https://www.youtube.com/watch?v=${MOCK_YOUTUBE_ID}`);
    await expect(docEditor.videoEmbedDialog.feedback).toHaveText("YouTube video detected.");

    await docEditor.videoEmbedDialog.insertButton.click();
    await docEditor.videoEmbedDialog.root.waitFor({ state: "detached" });

    await expect.poll(() => field.getEmbeddedIframeSrc()).toContain(`youtube.com/embed/${MOCK_YOUTUBE_ID}`);
  });

  test("As a user, I can embed a JoVE video into a document field", async ({ page, pageWorkspace }) => {
    await page.route(new RegExp(`^https://www\\.jove\\.com/embed/player\\?id=${MOCK_JOVE_ID}`), (route) =>
      route.fulfill({ contentType: "text/html", body: MOCK_JOVE_EMBED_BODY }),
    );

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    await field.clickToolbarButton("Embed video");
    await docEditor.videoEmbedDialog.waitForOpen();
    await docEditor.videoEmbedDialog.embedFromUrl(`https://www.jove.com/v/${MOCK_JOVE_ID}`);

    await expect.poll(() => field.getEmbeddedIframeSrc()).toContain(`jove.com/embed/player?id=${MOCK_JOVE_ID}`);
  });

  test("As a user, I can embed a TIB AV-Portal video into a document field", async ({ page, pageWorkspace }) => {
    await page.route(new RegExp(`^https://av\\.tib\\.eu/player/${MOCK_TIB_ID}`), (route) =>
      route.fulfill({ contentType: "text/html", body: MOCK_TIB_EMBED_BODY }),
    );

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    await field.clickToolbarButton("Embed video");
    await docEditor.videoEmbedDialog.waitForOpen();
    await docEditor.videoEmbedDialog.embedFromUrl(`https://av.tib.eu/media/${MOCK_TIB_ID}`);

    await expect.poll(() => field.getEmbeddedIframeSrc()).toContain(`av.tib.eu/player/${MOCK_TIB_ID}`);
  });
});

test.describe("Video embed (YouTube/JoVE/TIB AV-Portal) [real]", { tag: tags.APPS }, () => {
  test("As a user, I can embed a real YouTube video into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    await field.clickToolbarButton("Embed video");
    await docEditor.videoEmbedDialog.waitForOpen();
    await expect(docEditor.videoEmbedDialog.feedback).toHaveText("Paste a supported video URL.");

    await docEditor.videoEmbedDialog.urlInput.fill("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    await expect(docEditor.videoEmbedDialog.feedback).toHaveText("YouTube video detected.");

    await docEditor.videoEmbedDialog.insertButton.click();
    await docEditor.videoEmbedDialog.root.waitFor({ state: "detached" });

    await expect.poll(() => field.getEmbeddedIframeSrc()).toContain("youtube.com/embed/dQw4w9WgXcQ");
  });

  test("As a user, I can embed a real TIB AV-Portal video into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    await field.clickToolbarButton("Embed video");
    await docEditor.videoEmbedDialog.waitForOpen();
    await docEditor.videoEmbedDialog.embedFromUrl("https://av.tib.eu/media/10000");

    await expect.poll(() => field.getEmbeddedIframeSrc()).toContain("av.tib.eu/player/10000");
  });
});
