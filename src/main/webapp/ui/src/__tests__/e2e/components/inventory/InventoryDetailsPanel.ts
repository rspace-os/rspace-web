import { expect, type Locator, type Page } from "@playwright/test";
import { ToastsComponent } from "../shared/ToastsComponent";
import { AccessPermissionsEditor } from "./AccessPermissionsEditor";
import { AddToBasketDialogComponent } from "./AddToBasketDialogComponent";
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

export type InventoryIdentifierType = "IGSN" | "PIDINST";

const IDENTIFIER_CREATE_BUTTON_LABEL: Record<InventoryIdentifierType, string> = {
  IGSN: "Create new IGSN ID",
  PIDINST: "Create new PIDINST",
};

/** Per-attempt budget when acting on a toolbar action, so a reflow is retried rather than waited out. */
const ACTION_ATTEMPT_TIMEOUT = 2_000;

export type InventoryMoreAction =
  | "Edit"
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
  private readonly moreActionsButton: Locator;
  private readonly moreActions: MoreActionsMenu<InventoryMoreAction>;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("main");
    this.sections = new CollapsibleSections(this.root);
    this.heading = this.root.getByRole("heading", { level: 2 });
    this.permissionAlert = this.root.getByRole("alert").filter({ hasText: "You do not have permission to see" });
    this.moreActionsButton = this.root.getByRole("button", { name: "More actions" }).first();
    this.moreActions = new MoreActionsMenu(page, this.moreActionsButton);
  }

  /** The record title heading, matched by its name so another level-2 heading on the page can't stand in. */
  headingNamed(name: string): Locator {
    return this.root.getByRole("heading", { level: 2, name, exact: true });
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
    return openDialog(() => this.chooseAction("Move"), new MoveDialogComponent(this.page));
  }

  async openTransferDialog(): Promise<TransferDialogComponent> {
    return openDialog(() => this.chooseAction("Transfer"), new TransferDialogComponent(this.page));
  }

  async openCreateItemDialog(): Promise<CreateNewItemDialog> {
    return openDialog(() => this.clickAction("Create"), new CreateNewItemDialog(this.page));
  }

  async openAddToBasketDialog(): Promise<AddToBasketDialogComponent> {
    return openDialog(() => this.chooseAction("Add to Basket"), new AddToBasketDialogComponent(this.page));
  }

  async openExportDialog(): Promise<ExportDialogComponent> {
    return openDialog(() => this.chooseAction("Export"), new ExportDialogComponent(this.page));
  }

  async duplicate(): Promise<void> {
    await this.chooseAction("Duplicate");
  }

  /**
   * Runs `use` against the action's current control, retrying when the toolbar moves the action
   * between inline and More actions in the meantime.
   */
  async withActionControl(
    name: InventoryMoreAction,
    use: (control: Locator, timeout: number) => Promise<void>,
  ): Promise<void> {
    await expect(async () => {
      await use(await this.actionControl(name), ACTION_ATTEMPT_TIMEOUT);
    }).toPass({ timeout: 15_000 });
  }

  private async chooseAction(name: InventoryMoreAction): Promise<void> {
    await this.withActionControl(name, (control, timeout) => control.click({ timeout }));
  }

  private async actionControl(name: InventoryMoreAction): Promise<Locator> {
    const timeout = ACTION_ATTEMPT_TIMEOUT;
    await this.moreActions.close({ timeout });
    await this.moreActionsButton.waitFor({ state: "visible", timeout });
    const inline = this.root.getByRole("button", { name, exact: true });
    if ((await inline.count()) > 0) {
      return inline;
    }
    await this.moreActions.open({ timeout });
    return this.moreActions.item(name);
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

  async rename(name: string): Promise<void> {
    await this.root.getByRole("textbox", { name: "Name", exact: true }).fill(name);
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

  identifierCreateButton(identifierType: InventoryIdentifierType): Locator {
    return this.section("Identifiers").getByRole("button", { name: IDENTIFIER_CREATE_BUTTON_LABEL[identifierType] });
  }

  async createIdentifier(identifierType: InventoryIdentifierType = "IGSN"): Promise<IdentifierCreateDialog> {
    await this.expandSection("Identifiers");
    return openDialog(() => this.identifierCreateButton(identifierType).click(), new IdentifierCreateDialog(this.page));
  }

  async saveEdit(): Promise<void> {
    const cancel = this.root.getByRole("button", { name: "Cancel", exact: true });
    const errorToasts = new ToastsComponent(this.page).root.getByRole("group", { name: "error alert" });
    const errorsBefore = await errorToasts.allInnerTexts();
    await clickAndWaitDetached(this.root.getByRole("button", { name: "Save", exact: true }));

    let failure: string | undefined;
    await expect(async () => {
      if ((await cancel.count()) === 0) return;
      const errors = await errorToasts.allInnerTexts();
      failure = errors.find((text) => !errorsBefore.includes(text));
      if (failure === undefined && errors.length > errorsBefore.length) failure = errors.at(-1);
      if (failure !== undefined) return;
      throw new Error("The record is still saving");
    }).toPass({ timeout: 30_000 });
    if (failure !== undefined) throw new Error(`Saving the record failed: ${failure}`);
  }

  async emptyLocationCount(): Promise<number> {
    return this.section("Locations and Content")
      .getByText(/^[1-9]\d*$/)
      .count();
  }
}
