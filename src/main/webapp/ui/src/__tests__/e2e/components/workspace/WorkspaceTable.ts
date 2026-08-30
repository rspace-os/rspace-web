import type { ElementHandle, Locator, Page } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { MiniProfilePopover } from "./MiniProfilePopover";
import { WorkspaceSelectionBar } from "./WorkspaceSelectionBar";

export async function waitForTableSwap(page: Page, staleTable: ElementHandle | null): Promise<void> {
  if (!staleTable) return;
  await page
    .waitForFunction((oldEl) => document.querySelector("#file_table") !== oldEl, staleTable, { timeout: 5_000 })
    .catch(() => {});
}

export async function awaitTableRefresh(page: Page, trigger: () => Promise<void>): Promise<void> {
  const staleTable = await page.locator("#file_table").elementHandle();
  await trigger();
  await page.locator('#file_table [data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
  await waitForTableSwap(page, staleTable);
}

export class WorkspaceTable {
  readonly root: Locator;
  private readonly selectionBar: WorkspaceSelectionBar;

  constructor(private readonly page: Page) {
    this.root = page.locator("#file_table");
    this.selectionBar = new WorkspaceSelectionBar(page);
  }

  row(name: string): Locator {
    return this.root.getByRole("row").filter({
      has: this.page.getByRole("link", { name, exact: true }),
    });
  }

  checkbox(name: string): Locator {
    return this.row(name).getByRole("checkbox", { name: "Select record" });
  }

  globalIdLink(name: string): Locator {
    return this.row(name).getByRole("cell").nth(5).getByRole("link");
  }

  get selectAllCheckbox(): Locator {
    return this.root.getByRole("columnheader", { name: "Select/deselect all" }).getByRole("checkbox");
  }

  async selectRecord(name: string): Promise<void> {
    await this.selectRecords(name);
  }

  async selectRecords(...names: string[]): Promise<void> {
    for (const [i, name] of names.entries()) {
      await this.checkbox(name).check();
      if (i === 0) await this.selectionBar.waitUntilVisible();
    }
  }

  async deselectRecord(name: string): Promise<void> {
    await this.checkbox(name).uncheck();
  }

  async openRecord(name: string): Promise<void> {
    await this.row(name).getByRole("link", { name, exact: true }).click();
  }

  ownerButton(name: string): Locator {
    return this.row(name).getByRole("cell").last().getByRole("button");
  }

  async openOwnerMiniProfile(name: string): Promise<MiniProfilePopover> {
    await this.ownerButton(name).click();
    const popover = new MiniProfilePopover(this.page);
    await popover.waitUntilVisible();
    return popover;
  }

  async openInfoFor(name: string): Promise<RecordInfoDialog> {
    await this.row(name).getByRole("link", { name: "Record Info" }).click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async sortBy(column: "Name" | "Created" | "Modified"): Promise<void> {
    await awaitTableRefresh(this.page, async () => {
      await Promise.all([
        this.page.waitForResponse((res) => {
          const path = new URL(res.url()).pathname;
          return path.endsWith("/workspace/ajax/search") || path.includes("/workspace/ajax/view/");
        }),
        this.root.getByRole("columnheader", { name: column }).getByRole("link", { name: column }).click(),
      ]);
    });
  }

  get dataRows(): Locator {
    return this.root.locator("tbody tr");
  }

  async rowCount(): Promise<number> {
    return this.dataRows.count();
  }
}
