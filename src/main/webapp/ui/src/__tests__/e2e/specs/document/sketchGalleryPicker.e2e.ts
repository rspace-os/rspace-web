import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { imageNodesIn } from "@/__tests__/e2e/components/document/SketchDialogComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";

const currentDir = dirname(fileURLToPath(import.meta.url));
const IMAGE_PATH = resolve(currentDir, "../inventory/fixtures/container_background.png");
const PICKER_REQUEST = /\/gallery\/(getImageListFromRootImageFolder|getViewerImage\/|getThumbnail\/)/;

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
    const { width, height } = await page.evaluate(async (url) => {
      const image = new Image();
      image.src = url;
      await image.decode();
      return { width: image.naturalWidth, height: image.naturalHeight };
    }, viewerUrl);
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
});
