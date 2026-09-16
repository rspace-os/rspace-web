import type { Locator, Page } from "@playwright/test";

type KetcherWindow = {
  ketcher?: { setMolecule: (structure: string) => Promise<void>; getKet: () => Promise<string> };
};

export type KetcherDialogTitle = "Ketcher Insert Chemical" | "Ketcher Chemical Viewer (Read-Only)" | "Chemistry Search";

export class KetcherDialogComponent {
  readonly root: Locator;
  readonly canvas: Locator;
  readonly insertButton: Locator;
  readonly cancelButton: Locator;

  constructor(
    private readonly page: Page,
    title: KetcherDialogTitle = "Ketcher Insert Chemical",
  ) {
    this.root = page.getByRole("dialog", { name: title });
    this.canvas = this.root.locator('[data-testid="ketcher-canvas"][data-canvasmode="molecules-mode"]');
    this.insertButton = this.root.getByRole("button", { name: "Insert" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async uploadStructureFile(filePath: string): Promise<void> {
    await this.root.locator('[data-testid="open-file-button"]:visible').click();
    const openModal = this.page.locator('[data-testid="openStructureModal"]');
    await openModal.waitFor({ state: "visible" });
    const [chooser] = await Promise.all([
      this.page.waitForEvent("filechooser"),
      openModal.getByRole("button", { name: "Open from file" }).click(),
    ]);
    await chooser.setFiles(filePath);
    await this.page.getByRole("button", { name: "Add to Canvas" }).click();
    await openModal.waitFor({ state: "detached" });
    await this.page.waitForFunction(async () => {
      const ket = await (window as unknown as KetcherWindow).ketcher?.getKet();
      return typeof ket === "string" && Object.keys(JSON.parse(ket)).some((key) => key.startsWith("mol"));
    });
    await this.canvas.click();
  }

  /** Selects one of Ketcher's atom toolbar tools (e.g. "C", "P", "F") for placing on the canvas. */
  async selectAtomTool(symbol: string): Promise<void> {
    await this.root.locator(`[data-testid="${symbol}-button"]:visible`).click();
  }

  /** Places the currently-selected atom tool onto the canvas at the given position. */
  async clickCanvasAt(position: { x: number; y: number }): Promise<void> {
    await this.canvas.click({ position });
  }

  /** Opens Ketcher's native "Calculated Values" panel and returns its Chemical Formula line. */
  async getCalculatedFormula(): Promise<string> {
    await this.root.locator('[data-testid="Calculated Values button"]:visible').click();
    const formulaRow = this.page.getByText("Chemical Formula:").locator("xpath=parent::*").first();
    const text = await formulaRow.innerText();
    await this.page.getByRole("button", { name: "Close" }).click();
    return text.replace("Chemical Formula:", "").trim();
  }

  async isAtomToolEnabled(symbol: string): Promise<boolean> {
    return !(await this.root.locator(`[data-testid="${symbol}-button"]:visible`).first().isDisabled());
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.canvas.waitFor({ state: "visible" });
  }

  async setMoleculeFromSmiles(smiles: string): Promise<void> {
    await this.page.waitForFunction(() => (window as unknown as KetcherWindow).ketcher !== undefined);
    await this.page.evaluate(
      (structure) => (window as unknown as KetcherWindow).ketcher?.setMolecule(structure),
      smiles,
    );
    await this.canvas.click();
  }

  async insert(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/chemical/save",
      ),
      this.insertButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /chemical/save failed: ${response.status()} ${response.statusText()}`);
    }
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    const discardButton = this.page.getByRole("button", { name: "Discard" });
    if (await discardButton.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await discardButton.click();
    }
    await this.root.waitFor({ state: "detached" });
  }
}
