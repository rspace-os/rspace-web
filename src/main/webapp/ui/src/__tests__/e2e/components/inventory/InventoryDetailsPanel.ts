import type { Locator, Page } from "@playwright/test";
import { AccessPermissionsEditor } from "./AccessPermissionsEditor";
import { AttachmentsEditor } from "./AttachmentsEditor";
import { CollapsibleSections } from "./CollapsibleSections";
import { CreateNewItemDialog } from "./CreateNewItemDialog";
import { CustomFieldsEditor } from "./CustomFieldsEditor";
import { clickAndWaitDetached, openDialog } from "./DialogHelpers";
import { ExportDialogComponent } from "./ExportDialogComponent";
import { IdentifierCreateDialog, IdentifierPanel } from "./IdentifierPanel";
import { MoreActionsMenu } from "./MoreActionsMenu";
import { MoveDialogComponent } from "./MoveDialogComponent";
import { NotesEditor } from "./NotesEditor";
import { PrintOptionsDialogComponent } from "./PrintOptionsDialogComponent";
import { TransferDialogComponent } from "./TransferDialogComponent";

export type InventoryDetailAction = "Edit" | "Create" | "Duplicate" | "Move" | "Transfer";

export type InventoryMoreAction =
  | "Duplicate"
  | "Move"
  | "Transfer"
  | "Add to Basket"
  | "Export"
  | "Print Barcode"
  | "Trash";

export class InventoryDetailsPanel {
  readonly root: Locator;
  readonly heading: Locator;

  readonly permissionAlert: Locator;
  private readonly sections: CollapsibleSections;
  private readonly moreActions: MoreActionsMenu<InventoryMoreAction>;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("main");
    this.sections = new CollapsibleSections(this.root);
    this.heading = this.root.getByRole("heading", { level: 2 });
    this.permissionAlert = this.root.getByRole("alert");
    this.moreActions = new MoreActionsMenu(page, this.root.getByRole("button", { name: "More actions" }).first());
  }

  async name(): Promise<string> {
    return this.heading.innerText();
  }

  action(name: InventoryDetailAction): Locator {
    return this.root.getByRole("button", { name, exact: true });
  }

  async clickAction(name: InventoryDetailAction): Promise<void> {
    await this.action(name).click();
  }

  async openMoveDialog(): Promise<MoveDialogComponent> {
    return openDialog(
      () => this.moreActions.clickDirectOrFallback(this.action("Move"), "Move"),
      new MoveDialogComponent(this.page),
    );
  }

  async openTransferDialog(): Promise<TransferDialogComponent> {
    return openDialog(
      () => this.moreActions.clickDirectOrFallback(this.action("Transfer"), "Transfer"),
      new TransferDialogComponent(this.page),
    );
  }

  async openCreateItemDialog(): Promise<CreateNewItemDialog> {
    return openDialog(() => this.clickAction("Create"), new CreateNewItemDialog(this.page));
  }

  async openExportDialog(): Promise<ExportDialogComponent> {
    return openDialog(() => this.moreActions.click("Export"), new ExportDialogComponent(this.page));
  }

  async moreActionItem(name: InventoryMoreAction): Promise<Locator> {
    await this.moreActions.open();
    return this.moreActions.item(name);
  }

  async duplicateControl(): Promise<Locator> {
    const inline = this.action("Duplicate");
    if ((await inline.count()) > 0) {
      return inline;
    }
    return this.moreActionItem("Duplicate");
  }

  section(name: string): Locator {
    return this.sections.section(name);
  }

  async isSectionExpanded(name: string): Promise<boolean> {
    return this.sections.isExpanded(name);
  }

  async expandSection(name: string): Promise<void> {
    await this.sections.expand(name);
  }

  async collapseSection(name: string): Promise<void> {
    await this.sections.collapse(name);
  }

  async openPrintAllBarcodesDialog(): Promise<PrintOptionsDialogComponent> {
    await this.expandSection("Barcodes");
    return openDialog(
      () => this.section("Barcodes").getByRole("button", { name: "Print all barcodes" }).click(),
      new PrintOptionsDialogComponent(this.page),
    );
  }

  async enterEditMode(): Promise<void> {
    await this.clickAction("Edit");
    await this.root.getByRole("button", { name: "Save", exact: true }).waitFor({ state: "visible" });
  }

  customFields(): CustomFieldsEditor {
    return new CustomFieldsEditor(this.page, this.section("Custom Fields"));
  }

  accessPermissions(): AccessPermissionsEditor {
    return new AccessPermissionsEditor(this.page, this.section("Access Permissions"));
  }

  attachments(): AttachmentsEditor {
    return new AttachmentsEditor(this.page, this.section("Attachments"));
  }

  identifiers(): IdentifierPanel {
    return new IdentifierPanel(this.page, this.section("Identifiers"));
  }

  notes(): NotesEditor {
    return new NotesEditor(this.section("Notes"));
  }

  async createIdentifier(): Promise<IdentifierCreateDialog> {
    await this.expandSection("Identifiers");
    return openDialog(
      () => this.section("Identifiers").getByRole("button", { name: "Create new IGSN ID" }).click(),
      new IdentifierCreateDialog(this.page),
    );
  }

  async saveEdit(): Promise<void> {
    await clickAndWaitDetached(this.root.getByRole("button", { name: "Save", exact: true }));
  }

  async emptyLocationCount(): Promise<number> {
    return this.section("Locations and Content")
      .getByText(/^[1-9]\d*$/)
      .count();
  }
}
