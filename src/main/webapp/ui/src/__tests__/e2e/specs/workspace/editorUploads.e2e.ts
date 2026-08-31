import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";

const currentDir = dirname(fileURLToPath(import.meta.url));
const IMAGE_PATH = resolve(currentDir, "../inventory/fixtures/container_preview.png");

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
});
