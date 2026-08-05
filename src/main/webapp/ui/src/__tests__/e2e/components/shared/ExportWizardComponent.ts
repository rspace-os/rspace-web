import type { Locator, Page } from "@playwright/test";

export type ExportFormat = "pdf" | "doc" | "xml" | "html" | "eln";

const FORMAT_ACCESSIBLE_NAME: Record<ExportFormat, string> = {
  pdf: "PDF file",
  doc: ".DOC file",
  xml: ".ZIP bundle containing .XML files",
  html: ".ZIP bundle containing .HTML files",
  eln: "RO-Crate",
};

export type LinkedDocumentsDepth = "none" | 1 | 2 | 3 | "infinity";

const LINKED_DOCUMENTS_OPTION_TEXT: Record<LinkedDocumentsDepth, string> = {
  none: "Don't include linked documents",
  1: "Include linked documents to depth 1",
  2: "Include linked documents to depth 2",
  3: "Include linked documents to depth 3",
  infinity: "Include linked documents to depth infinity",
};

export class ExportWizardComponent {
  readonly root: Locator;
  readonly backButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog");
    this.backButton = this.root.getByRole("button", { name: "Back", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectFormat(format: ExportFormat): Promise<void> {
    await this.root.getByRole("radio", { name: FORMAT_ACCESSIBLE_NAME[format] }).click();
  }

  async setExportToRepository(enabled: boolean): Promise<void> {
    const toggle = this.root.getByRole("checkbox", { name: "Export to a repository", exact: true });
    if ((await toggle.isChecked()) !== enabled) {
      await toggle.click();
    }
  }

  async selectRepository(repoDisplayName: string, alias?: string): Promise<void> {
    const accessibleName = alias === undefined ? repoDisplayName : `${repoDisplayName} - ${alias}`;
    await this.root.getByRole("radio", { name: accessibleName, exact: true }).click();
  }

  async fillTitle(title: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Title", exact: true }).fill(title);
  }

  async fillDescription(description: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Description", exact: true }).fill(description);
  }

  async selectRepositorySubject(subject: string): Promise<void> {
    await this.root.getByRole("combobox", { name: "Subject", exact: false }).click();
    await this.root.page().getByRole("option", { name: subject, exact: true }).click();
  }

  async selectRepositoryLicense(license: string): Promise<void> {
    await this.root.getByRole("combobox", { name: "License", exact: false }).click();
    await this.root.page().getByRole("option", { name: license, exact: true }).click();
  }

  async fillFileName(name: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "File name", exact: true }).fill(name);
  }

  async selectLinkedDocumentsDepth(depth: LinkedDocumentsDepth): Promise<void> {
    await this.root
      .getByRole("combobox", { name: "Should linked RSpace documents be included in export?", exact: true })
      .click();
    await this.root.page().getByRole("option", { name: LINKED_DOCUMENTS_OPTION_TEXT[depth] }).click();
  }

  async fillExportDescription(description: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Export Description (optional)" }).fill(description);
  }

  async next(): Promise<void> {
    await this.root.getByRole("button", { name: "Next", exact: true }).click();
  }

  async submit(): Promise<void> {
    await this.root.getByRole("button", { name: "Export", exact: true }).click();
  }
}
