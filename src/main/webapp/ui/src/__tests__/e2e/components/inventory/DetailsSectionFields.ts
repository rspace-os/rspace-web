import type { Locator, Page } from "@playwright/test";
import { uploadViaFileChooser } from "./DialogHelpers";

export async function setDescriptionField(detailsSection: Locator, text: string): Promise<void> {
  const description = detailsSection.getByRole("group", { name: "Description" });
  const paragraph = description.locator("iframe").contentFrame().getByRole("paragraph");
  await paragraph.click();
  await paragraph.fill(text);
}

export async function addTagField(page: Page, detailsSection: Locator, name: string): Promise<void> {
  await detailsSection.getByRole("group", { name: "Tags" }).getByRole("button", { name: "Add Tag" }).click();
  await page.getByRole("combobox", { name: "Filter suggested tags" }).fill(name);
  await page.keyboard.press("Enter");
}

export async function uploadPreviewImageField(page: Page, root: Locator, path: string): Promise<void> {
  await uploadViaFileChooser(
    page,
    root.getByRole("group", { name: "Preview Image" }).getByRole("button", { name: "Add Image" }),
    path,
  );
}
