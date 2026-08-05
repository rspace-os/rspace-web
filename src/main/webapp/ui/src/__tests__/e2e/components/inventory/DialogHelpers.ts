import type { Locator, Page } from "@playwright/test";

export async function openDialog<T extends { waitForOpen(): Promise<void> }>(
  trigger: () => Promise<void>,
  dialog: T,
): Promise<T> {
  await trigger();
  await dialog.waitForOpen();
  return dialog;
}

export async function clickAndWaitDetached(button: Locator, detachTarget: Locator = button): Promise<void> {
  await button.click();
  await detachTarget.waitFor({ state: "detached" });
}

export async function uploadViaFileChooser(page: Page, trigger: Locator, path: string): Promise<void> {
  const chooserPromise = page.waitForEvent("filechooser");
  await trigger.click();
  const chooser = await chooserPromise;
  await chooser.setFiles(path);
}

export async function dismissMobileDatePickerIfPresent(datePicker: Locator): Promise<void> {
  const okButton = datePicker.getByRole("button", { name: "OK", exact: true });
  if (await okButton.isVisible().catch(() => false)) {
    await okButton.click();
  }
}
