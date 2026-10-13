import type { Locator, Page } from "@playwright/test";
import { AddToBasketDialogComponent } from "./AddToBasketDialogComponent";
import { BatchEditFormComponent } from "./BatchEditFormComponent";
import { openDialog } from "./DialogHelpers";
import { MoreActionsMenu } from "./MoreActionsMenu";
import { MoveDialogComponent } from "./MoveDialogComponent";
import { PrintOptionsDialogComponent } from "./PrintOptionsDialogComponent";
import { ProcessWizardComponent } from "./ProcessWizardComponent";

export type InventoryBatchAction =
  | "Process"
  | "Duplicate"
  | "Move"
  | "Transfer"
  | "Add to Basket"
  | "Export"
  | "Print Barcode"
  | "Trash"
  | "Restore";

export class InventoryBatchActionBar {
  readonly root: Locator;
  private readonly moreActions: MoreActionsMenu<InventoryBatchAction>;

  constructor(
    private readonly page: Page,
    resultsTable: Locator,
  ) {
    this.root = resultsTable.getByRole("columnheader");
    this.moreActions = new MoreActionsMenu(page, this.root.getByRole("button", { name: "More actions" }));
  }

  /** Opens the More actions menu and returns one of its items, which may be absent. */
  async moreActionItem(name: InventoryBatchAction): Promise<Locator> {
    await this.moreActions.open();
    return this.moreActions.item(name);
  }

  async clickMoreAction(name: InventoryBatchAction): Promise<void> {
    await this.moreActions.click(name);
  }

  async openMoveDialog(): Promise<MoveDialogComponent> {
    return openDialog(
      () =>
        this.moreActions.clickDirectOrFallback(this.root.getByRole("button", { name: "Move", exact: true }), "Move"),
      new MoveDialogComponent(this.page),
    );
  }

  /** Shown only while inventory.operations.available is ALLOWED. */
  async openProcessWizard(): Promise<ProcessWizardComponent> {
    return openDialog(() => this.moreActions.click("Process"), new ProcessWizardComponent(this.page));
  }

  async openBatchEdit(): Promise<BatchEditFormComponent> {
    return openDialog(
      () => this.root.getByRole("button", { name: "Batch Edit", exact: true }).click(),
      new BatchEditFormComponent(this.page),
    );
  }

  async restore(): Promise<void> {
    await this.moreActions.clickDirectOrFallback(
      this.root.getByRole("button", { name: "Restore", exact: true }),
      "Restore",
    );
  }

  async openAddToBasketDialog(): Promise<AddToBasketDialogComponent> {
    return openDialog(() => this.moreActions.click("Add to Basket"), new AddToBasketDialogComponent(this.page));
  }

  async openPrintOptionsDialog(): Promise<PrintOptionsDialogComponent> {
    return openDialog(() => this.moreActions.click("Print Barcode"), new PrintOptionsDialogComponent(this.page));
  }
}
