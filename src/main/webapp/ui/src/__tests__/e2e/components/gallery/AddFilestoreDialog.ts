import { expect, type Locator, type Page } from "@playwright/test";
import { responseTo } from "@/__tests__/e2e/responses";
import { type FilestoreCredentials, FilestoreLoginDialog } from "./FilestoreLoginDialog";

export interface NewFilestore {
  fileSystem: string;
  folder: string;
  name: string;
  credentials?: FilestoreCredentials;
}

export class AddFilestoreDialog {
  readonly root: Locator;
  private readonly cancelButton: Locator;
  private readonly loginDialog: FilestoreLoginDialog;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Add Filestore" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.loginDialog = new FilestoreLoginDialog(page);
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  fileSystemOption(name: string): Locator {
    return this.root.getByRole("radio", { name, exact: true });
  }

  async addFilestore({ fileSystem, folder, name, credentials }: NewFilestore): Promise<void> {
    await this.fileSystemOption(fileSystem).check();
    await this.root.getByRole("button", { name: "Choose Filesystem" }).click();
    if (credentials) {
      await this.loginDialog.login(credentials);
    }
    await this.selectFolder(folder);
    await this.root.getByRole("button", { name: "Choose Folder" }).click();
    await this.root.getByRole("textbox", { name: "Filestore name" }).fill(name);
    await responseTo(
      this.page,
      "POST",
      ({ pathname }) => pathname === "/api/v1/gallery/filestores",
      () => this.root.getByRole("button", { name: "Add Filestore" }).click(),
    );
    await this.root.waitFor({ state: "hidden" });
  }

  private async selectFolder(folder: string): Promise<void> {
    const treeItem = this.root.getByRole("treeitem", { name: folder, exact: true });
    const response = await responseTo(
      this.page,
      "GET",
      ({ pathname, searchParams }) => pathname.endsWith("/browse") && searchParams.get("remotePath") === `/${folder}/`,
      () => treeItem.getByText(folder, { exact: true }).click(),
    );
    const { content } = (await response.json()) as { content: { name: string; folder: boolean }[] };
    // Subfolders slide open and push Choose Folder down; a click during that slide is lost.
    // MUI Collapse sets its height to auto only once the slide has ended.
    if (content.some((entry) => entry.folder)) {
      const subfolders = treeItem.getByRole("group");
      await expect.poll(() => subfolders.evaluate((group: HTMLElement) => group.style.height)).toBe("auto");
    }
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
