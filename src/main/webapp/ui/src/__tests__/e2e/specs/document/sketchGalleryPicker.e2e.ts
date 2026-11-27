import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type Locator, type Page } from "@playwright/test";
import { imageNodesIn, type SketchDialogComponent } from "@/__tests__/e2e/components/document/SketchDialogComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";

const currentDir = dirname(fileURLToPath(import.meta.url));
const IMAGE_PATH = resolve(currentDir, "../inventory/fixtures/container_background.png");
const PICKER_REQUEST = /\/gallery\/(getImageListFromRootImageFolder|getViewerImage\/|getThumbnail\/)/;

const isSave = (url: string) => new URL(url).pathname.endsWith("/saveImageAnnotation");

/** Opens the Sketch tool's Gallery tab on a new document's image; returns its first tile and that image's viewer URL. */
async function openGalleryPicker(pageWorkspace: WorkspacePage) {
  await pageWorkspace.open();
  const editor = await pageWorkspace.createBasicDocument();
  const field = await editor.getField("New List of Materials");
  await field.insertImageAttachment(IMAGE_PATH);
  const sketch = await (await field.selectImage()).openSketch();
  await sketch.openGalleryTab();
  const tile = sketch.galleryTiles.first();
  await expect(tile).toBeVisible();
  const imageId = /getThumbnail\/(\d+)\//.exec((await tile.getAttribute("src")) ?? "")?.[1];
  expect(imageId).toBeDefined();
  return { sketch, tile, viewerUrl: `/gallery/getViewerImage/${imageId}` };
}

/** Holds every request for url until release() is called. */
async function holdRequests(page: Page, url: string) {
  let release = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**${url}`, async (route) => {
    await released;
    await route.continue();
  });
  return release;
}

/** The viewer image's natural size. */
function imageSize(page: Page, url: string) {
  return page.evaluate(async (src) => {
    const image = new Image();
    image.src = src;
    await image.decode();
    return { width: image.naturalWidth, height: image.naturalHeight };
  }, url);
}

async function clickTile(sketch: SketchDialogComponent, tile: Locator) {
  await sketch.openGalleryTab();
  await tile.click();
  await expect(sketch.galleryPicker).toBeHidden();
}

test.describe("Sketch tool Gallery picker", () => {
  test("As a user, opening the Sketch tool loads no Gallery images until I open its Gallery tab", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("New List of Materials");
    await field.insertImageAttachment(IMAGE_PATH);

    const pickerRequests: string[] = [];
    page.on("request", (request) => {
      const { pathname } = new URL(request.url());
      if (PICKER_REQUEST.test(pathname)) pickerRequests.push(pathname);
    });
    const listRequests = () => pickerRequests.filter((path) => path.includes("getImageListFromRootImageFolder"));

    const toolbar = await field.selectImage();
    const sketch = await toolbar.openSketch();
    expect(pickerRequests).toEqual([]);

    await sketch.openGalleryTab();
    const tile = sketch.galleryTiles.first();
    await expect(tile).toBeVisible();
    await expect(tile).toHaveAttribute("loading", "lazy");
    await expect(tile).toHaveAttribute("src", /^\/gallery\/getThumbnail\/\d+\//);
    const tileCount = await sketch.galleryTiles.count();

    await sketch.openTemplatesTab();
    await sketch.openGalleryTab();
    expect(listRequests()).toHaveLength(1);
    await expect(sketch.galleryTiles).toHaveCount(tileCount);
    expect(pickerRequests.filter((path) => path.includes("getViewerImage"))).toEqual([]);

    const imageId = /getThumbnail\/(\d+)\//.exec((await tile.getAttribute("src")) ?? "")?.[1];
    expect(imageId).toBeDefined();
    const viewerUrl = `/gallery/getViewerImage/${imageId}`;

    // Hold the viewer image so the drawing is checked before its size is known.
    let releaseViewerImage = () => {};
    const viewerImageReleased = new Promise<void>((resolve) => {
      releaseViewerImage = resolve;
    });
    await page.route(`**${viewerUrl}`, async (route) => {
      await viewerImageReleased;
      await route.continue();
    });

    // Clicking a tile adds the image as dropping it does, at (300, 300).
    await tile.click();
    await expect(sketch.galleryPicker).toBeHidden();
    // The node exists before the image loads, so an early Insert still saves it.
    const [undone] = await sketch.imageNodes(viewerUrl);
    expect(undone).toMatchObject({ x: 300, y: 300 });

    // Zwibbler gives a new node the id of an undone one; centring the undone drop must not move it.
    await sketch.undo();
    expect(await sketch.imageNodes(viewerUrl)).toEqual([]);
    const otherUrl = "/scripts/zwibbler2/wd-pick.png";
    expect(await sketch.addImageNode(otherUrl, 300, 300)).toBe(undone.id);

    await sketch.openGalleryTab();
    await tile.click();
    await expect(sketch.galleryPicker).toBeHidden();

    const saveRequests: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.endsWith("/saveImageAnnotation")) saveRequests.push(request.postData() ?? "");
    });
    // Insert waits for the image, so the saved picture includes it; a second click must not save twice.
    await sketch.insertButton.click();
    await sketch.insertButton.click();
    // There is no event for "not sent"; give a premature save time to show up.
    await page.waitForTimeout(1000);
    expect(saveRequests).toEqual([]);

    const savedResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname.endsWith("/saveImageAnnotation"),
    );
    releaseViewerImage();
    expect((await savedResponse).ok()).toBe(true);
    await expect(sketch.root).toBeHidden();
    await page.unroute(`**${viewerUrl}`);
    expect(saveRequests).toHaveLength(1);

    const annotations = new URLSearchParams(saveRequests[0]).get("annotations") ?? "";
    const { width, height } = await imageSize(page, viewerUrl);
    expect(imageNodesIn(annotations, otherUrl)).toMatchObject([{ x: 300, y: 300 }]);
    // Centred using the viewer image's size, far larger than the tile.
    expect(imageNodesIn(annotations, viewerUrl)).toMatchObject([{ x: 300 - width / 2, y: 300 - height / 2 }]);

    // The saved picture is rendered on Insert, so it must show the dropped image, not an empty box. The fixture is
    // solid grey; the background copy covers 0..width, so this point, inside the drop, is grey only if it was drawn.
    const png = new URLSearchParams(saveRequests[0]).get("image") ?? "";
    const pixel = await page.evaluate(
      async ({ src, x, y }) => {
        const image = new Image();
        image.src = src;
        await image.decode();
        const canvas = document.createElement("canvas");
        canvas.width = image.naturalWidth;
        canvas.height = image.naturalHeight;
        const context = canvas.getContext("2d");
        context?.drawImage(image, 0, 0);
        return Array.from(context?.getImageData(x, y, 1, 1).data ?? []);
      },
      { src: png, x: 300 + width / 2 - 10, y: 300 + height / 2 - 10 },
    );
    expect(pixel).toEqual([200, 200, 200, 255]);
  });

  test("As a user, Insert does not wait for a Gallery image I removed while it was downloading", async ({
    page,
    pageWorkspace,
  }) => {
    const { sketch, tile, viewerUrl } = await openGalleryPicker(pageWorkspace);
    const release = await holdRequests(page, viewerUrl);
    try {
      await clickTile(sketch, tile);
      await sketch.undo();
      expect(await sketch.imageNodes(viewerUrl)).toEqual([]);
      // The image is still held, so this saves only if Insert ignores the undone drop (it would wait 15 s).
      const saved = page.waitForResponse((response) => isSave(response.url()), { timeout: 5000 });
      await sketch.insertButton.click();
      expect((await saved).ok()).toBe(true);
      await expect(sketch.root).toBeHidden();
    } finally {
      release();
    }
  });

  test("As a user, Insert stops waiting when I undo a Gallery image during the wait", async ({
    page,
    pageWorkspace,
  }) => {
    const { sketch, tile, viewerUrl } = await openGalleryPicker(pageWorkspace);
    const release = await holdRequests(page, viewerUrl);
    try {
      await clickTile(sketch, tile);
      const saves: string[] = [];
      page.on("request", (request) => {
        if (isSave(request.url())) saves.push(request.url());
      });
      await sketch.insertButton.click();
      // There is no event for "not sent"; give a premature save time to show up.
      await page.waitForTimeout(1000);
      expect(saves).toEqual([]);
      const saved = page.waitForResponse((response) => isSave(response.url()), { timeout: 5000 });
      await sketch.undo();
      expect((await saved).ok()).toBe(true);
    } finally {
      release();
    }
  });

  test("As a user, Insert saves a resized Gallery image as soon as it is drawn", async ({ page, pageWorkspace }) => {
    const { sketch, tile, viewerUrl } = await openGalleryPicker(pageWorkspace);
    const release = await holdRequests(page, viewerUrl);
    await clickTile(sketch, tile);
    // Select the 100 x 20 placeholder by moving it (a click starts cropping), then drag its right-hand handle.
    await sketch.drag({ x: 350, y: 310 }, { x: 360, y: 310 });
    await sketch.drag({ x: 410, y: 310 }, { x: 510, y: 310 });
    // Resized, so its size on the canvas never equals the image's own size.
    expect(await sketch.imageNodes(viewerUrl)).toMatchObject([{ scaleX: 2 }]);
    await sketch.insertButton.click();

    const saved = page.waitForResponse((response) => isSave(response.url()));
    const releasedAt = Date.now();
    release();
    expect((await saved).ok()).toBe(true);
    // Waiting for the canvas size to match the image's would add a 5 s timeout here.
    expect(Date.now() - releasedAt).toBeLessThan(3000);
  });

  test("As a user, a Gallery image I drag while it downloads keeps its centring", async ({ page, pageWorkspace }) => {
    const { sketch, tile, viewerUrl } = await openGalleryPicker(pageWorkspace);
    const release = await holdRequests(page, viewerUrl);
    await clickTile(sketch, tile);
    expect(await sketch.imageNodes(viewerUrl)).toMatchObject([{ x: 300, y: 300 }]);

    // Drag the 100 x 20 placeholder, and let the image arrive before the drag ends.
    const start = await sketch.pagePoint(350, 310);
    const unit = await sketch.pagePoint(351, 311);
    const drag = { x: 60, y: 40 };
    await page.mouse.move(start.x, start.y);
    await page.mouse.down();
    await page.mouse.move(start.x + drag.x, start.y + drag.y, { steps: 5 });
    const loaded = page.waitForResponse((response) => new URL(response.url()).pathname === viewerUrl);
    release();
    await loaded;
    // Long enough for the image to be centred, were that done during the drag.
    await page.waitForTimeout(500);
    await page.mouse.up();
    await page.unroute(`**${viewerUrl}`);

    const { width, height } = await imageSize(page, viewerUrl);
    const centred = { x: 300 - width / 2, y: 300 - height / 2 };
    const moved = { x: centred.x + drag.x / (unit.x - start.x), y: centred.y + drag.y / (unit.y - start.y) };
    await expect
      .poll(async () => {
        const [node] = await sketch.imageNodes(viewerUrl);
        return Math.abs(node.x - moved.x) < 2 && Math.abs(node.y - moved.y) < 2;
      })
      .toBe(true);

    // Undoing the drag puts the image back centred on its drop point.
    await sketch.undo();
    expect(await sketch.imageNodes(viewerUrl)).toMatchObject([centred]);
  });
});
