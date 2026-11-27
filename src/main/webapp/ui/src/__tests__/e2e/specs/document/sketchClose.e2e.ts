import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";

for (const { name, touch, description } of [
  { name: "Close", touch: false, description: "the title-bar Close button" },
  { name: "Cancel", touch: false, description: "the footer Cancel button" },
  { name: "Close", touch: true, description: "a touch tap on the title-bar Close button" },
]) {
  test.describe(description, () => {
    test.use({ hasTouch: touch });

    test(`As a user, I can keep or discard a sketch using ${description}`, async ({ page, pageWorkspace }) => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("", 0);
      const sketch = page.getByRole("dialog", { name: "Sketch", exact: true });
      const sketchFrame = sketch.locator("iframe").contentFrame();
      // Zwibbler's drawing canvas has no accessible name.
      const canvas = sketchFrame.locator("#image-editorSketch canvas[tabindex]");
      const warning = page.getByText(/please confirm that you wish to cancel/i);
      const keepDrawing = page.getByRole("button", { name: "No, don't", exact: true });
      const discardDrawing = page.getByRole("button", { name: "Yes, cancel", exact: true });

      await field.openMenu("Insert");
      await field.menuItem("Sketch").click();
      await sketchFrame.locator('[title="Small brush"]').click();

      const emptyCanvas = await canvas.evaluate((element: HTMLCanvasElement) => element.toDataURL());
      const bounds = await canvas.boundingBox();
      if (!bounds) throw new Error("Sketch canvas is hidden");
      await page.mouse.move(bounds.x + 100, bounds.y + 100);
      await page.mouse.down();
      await page.mouse.move(bounds.x + 200, bounds.y + 160, { steps: 10 });
      await page.mouse.up();
      await expect
        .poll(() => canvas.evaluate((element: HTMLCanvasElement) => element.toDataURL()))
        .not.toBe(emptyCanvas);
      const drawing = await canvas.evaluate((element: HTMLCanvasElement) => element.toDataURL());

      const close = sketch.getByRole("button", { name, exact: true });
      if (touch) {
        await close.tap();
      } else {
        await close.click();
      }
      await expect(warning, `The ${name} button should prompt before discarding`).toBeVisible();
      await keepDrawing.click();
      await expect(warning).toBeHidden();
      await expect(canvas).toBeVisible();
      expect(await canvas.evaluate((element: HTMLCanvasElement) => element.toDataURL())).toBe(drawing);

      if (!touch && name === "Close") {
        await close.press("Space");
        await expect(warning).toHaveCount(1);
        await expect(warning).toBeVisible();
        await keepDrawing.click();
        await expect(warning, "Declining once should dismiss the Space confirmation").toBeHidden();
        expect(await canvas.evaluate((element: HTMLCanvasElement) => element.toDataURL())).toBe(drawing);

        await close.press("Enter");
      } else if (touch) {
        await close.tap();
      } else {
        await close.click();
      }
      await expect(warning).toBeVisible();
      await discardDrawing.click();
      await expect(sketch).toBeHidden();
      await expect(warning).toBeHidden();
    });
  });
}
