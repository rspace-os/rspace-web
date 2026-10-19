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
    await openModal.locator('[data-testid="open-from-file-button"] input[type="file"]').setInputFiles(filePath);
    await this.page.getByRole("button", { name: "Add to Canvas" }).click();
    await openModal.waitFor({ state: "detached" });
    await this.waitForMoleculeLoaded();
    await this.canvas.click();
  }

  // Ketcher 3.18's KET schema nests molecule data under root.nodes, not the older mol0/mol1/... keys.
  private async waitForMoleculeLoaded(): Promise<void> {
    await this.page.waitForFunction(async () => {
      const ket = await (window as unknown as KetcherWindow).ketcher?.getKet();
      if (typeof ket !== "string") return false;
      const parsed = JSON.parse(ket) as { root?: { nodes?: unknown[] } };
      return (parsed.root?.nodes?.length ?? 0) > 0;
    });
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
    const formulaRow = this.page.locator('[data-testid="Chemical Formula-wrapper"]');
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

  // window.ketcher exists before its WASM parser is ready: setMolecule can silently no-op,
  // or briefly "take" then revert to empty on its own. No DOM signal marks true readiness,
  // so this retries and reconfirms the molecule is still there before trusting it.
  async setMoleculeFromSmiles(smiles: string): Promise<void> {
    await this.page.waitForFunction(() => (window as unknown as KetcherWindow).ketcher !== undefined);
    for (let attempt = 0; attempt < 5; attempt++) {
      await this.page.evaluate(
        (structure) => (window as unknown as KetcherWindow).ketcher?.setMolecule(structure),
        smiles,
      );
      if (await this.hasMoleculeLoaded()) {
        await this.page.waitForTimeout(300);
        if (await this.hasMoleculeLoaded()) {
          await this.canvas.click();
          return;
        }
      }
    }
    throw new Error(`setMoleculeFromSmiles: molecule for "${smiles}" did not stay loaded after 5 attempts`);
  }

  private async hasMoleculeLoaded(): Promise<boolean> {
    const ket = await this.page.evaluate(async () => (window as unknown as KetcherWindow).ketcher?.getKet());
    if (typeof ket !== "string") return false;
    const parsed = JSON.parse(ket) as { root?: { nodes?: unknown[] } };
    return (parsed.root?.nodes?.length ?? 0) > 0;
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
    await Promise.race([discardButton.waitFor({ state: "visible" }), this.root.waitFor({ state: "detached" })]);
    if (await discardButton.isVisible()) {
      await discardButton.click();
    }
    await this.root.waitFor({ state: "detached" });
  }
}
