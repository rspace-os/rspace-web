import { expect, type Locator, type Page } from "@playwright/test";
import { StoichiometryTableComponent } from "@/__tests__/e2e/components/document/StoichiometryTableComponent";

export class StoichiometryReactionDialogComponent extends StoichiometryTableComponent {
  private readonly addChemicalButton: Locator;
  private readonly saveChangesButton: Locator;
  private readonly closeButton: Locator;
  private readonly deleteButton: Locator;

  constructor(private readonly page: Page) {
    const dialogRoot = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Reaction Table" }) });
    super(dialogRoot);
    this.addChemicalButton = dialogRoot.getByRole("button", { name: "Add Chemical", exact: true });
    this.saveChangesButton = dialogRoot.getByRole("button", { name: "Save Changes" });
    this.closeButton = dialogRoot.getByRole("button", { name: "Close", exact: true });
    this.deleteButton = dialogRoot.getByRole("button", { name: "Delete", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async addSmilesManually(name: string, smiles: string): Promise<void> {
    await this.addChemicalButton.click();
    await this.page.getByRole("menuitem", { name: "Manually Manually enter SMILES", exact: true }).click();

    const manualEntryDialog = this.page.getByRole("dialog", { name: "Add New Chemical" });
    await manualEntryDialog.getByRole("textbox", { name: "Name" }).fill(name);
    await manualEntryDialog.getByRole("textbox", { name: "SMILES String" }).fill(smiles);
    await this.waitForMoleculeLookup(() =>
      manualEntryDialog.getByRole("button", { name: "Add Chemical", exact: true }).click(),
    );
    await manualEntryDialog.waitFor({ state: "hidden" });

    await this.rowByCompoundName(name).waitFor({ state: "visible" });
  }

  async addPubChemCompound(name: string): Promise<void> {
    await this.addChemicalButton.click();
    await this.page.getByRole("menuitem", { name: "PubChem Import compound from PubChem", exact: true }).click();

    const pubchemDialog = this.page.getByRole("dialog", { name: "Insert from PubChem" });
    await pubchemDialog.getByPlaceholder("Enter a compound name or CAS number").fill(name);
    await pubchemDialog.getByRole("button", { name: "Search", exact: true }).click();
    await pubchemDialog
      .getByRole("region", { name, exact: true })
      .getByRole("checkbox", { name: "Select compound" })
      .check();
    await this.waitForMoleculeLookup(() => pubchemDialog.getByRole("button", { name: "Insert", exact: true }).click());
    await pubchemDialog.waitFor({ state: "hidden" });

    await this.rowByCompoundName(name).waitFor({ state: "visible", timeout: 15_000 });
  }

  async saveChanges(): Promise<void> {
    await this.saveChangesButton.click();
    // The dialog clears its dirty state only after the persisted revision returns.
    await this.saveChangesButton.waitFor({ state: "hidden" });
  }

  private async waitForMoleculeLookup(submit: () => Promise<void>): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().endsWith("/stoichiometry/molecule/info") && res.request().method() === "POST",
      ),
      submit(),
    ]);
    expect(response.ok(), `Molecule lookup returned HTTP ${response.status()}: ${await response.text()}`).toBe(true);
  }

  async delete(): Promise<void> {
    await this.deleteButton.click();
  }

  async close(): Promise<void> {
    const discardButton = this.page.getByRole("dialog", { name: "Discard changes?" }).getByRole("button", {
      name: "Discard",
      exact: true,
    });
    await expect(async () => {
      if (!(await this.root.isVisible().catch(() => false))) {
        return;
      }
      await this.closeButton.click();
      if (await discardButton.isVisible({ timeout: 1_000 }).catch(() => false)) {
        await discardButton.click();
      }
      await this.root.waitFor({ state: "hidden", timeout: 5_000 });
    }).toPass({ timeout: 30_000 });
  }
}
