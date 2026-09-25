import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";

export class DocumentRevisionsPage extends BasePage {
  readonly path = "/workspace/revisionHistory/list";

  private documentId: number | undefined;

  async openForDocument(documentId: number): Promise<void> {
    this.documentId = documentId;
    await this.page.goto(`${this.path}/${documentId}`);
    await this.page.getByRole("link", { name: "Back to Workspace" }).waitFor({ state: "visible" });
  }

  /** Global IDs for this document's own revisions, e.g. "SD2843v1" — never another document's. */
  private versionLinkPrefix(): string {
    if (this.documentId === undefined) throw new Error("Call openForDocument() before reading revisions.");
    return `SD${this.documentId}v`;
  }

  private versionRow(version: number): Locator {
    return this.page
      .locator("#documentHistory")
      .getByRole("row")
      .filter({
        has: this.page.getByRole("link", { name: `${this.versionLinkPrefix()}${version}`, exact: true }),
      });
  }

  async latestVersion(): Promise<number> {
    const ids = await this.page
      .locator("#documentHistory")
      .getByRole("link", { name: this.versionLinkPrefix(), exact: false })
      .allTextContents();
    if (!ids.length) throw new Error("Revision history contains no document versions.");
    return Math.max(...ids.map((id) => Number(id.slice(id.lastIndexOf("v") + 1))));
  }

  async openVersion(version: number): Promise<void> {
    await this.versionRow(version).getByRole("link", { name: "View", exact: true }).click();
    await this.page.waitForURL("**/structuredDocument/audit/view?**");
  }
}
