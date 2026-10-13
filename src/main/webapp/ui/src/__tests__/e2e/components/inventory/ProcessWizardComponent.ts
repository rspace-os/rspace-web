import { expect, type Locator, type Page } from "@playwright/test";
import { PlacementStepComponent } from "./PlacementStepComponent";

export type WizardOperation = "Aliquot" | "Passage" | "Pool" | "Derive" | "Cryopreserve" | "Revive" | "Destroy";

export type WizardStep = "Details" | "Template" | "Amounts" | "Documentation" | "Location" | "Confirm";

/** The operation wizard opened by the Process action, from choosing an operation to Perform. */
export class ProcessWizardComponent {
  readonly root: Locator;
  readonly location: PlacementStepComponent;
  readonly nextButton: Locator;
  readonly performButton: Locator;
  readonly reviewEditButton: Locator;
  readonly rememberCheckbox: Locator;
  /** The note shown when a remembered container can no longer take the new subsamples. */
  readonly rememberedContainerNote: Locator;
  private readonly cancelButton: Locator;
  private readonly backButton: Locator;
  private readonly currentStep: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", {
      name: /^(Process subsample|Aliquot|Passage|Pool|Derive|Cryopreserve|Revive|Destroy)\b/,
    });
    this.location = new PlacementStepComponent(page, this.root);
    this.nextButton = this.root.getByRole("button", { name: "Next", exact: true });
    this.performButton = this.root.getByRole("button", { name: "Perform", exact: true });
    this.reviewEditButton = this.root.getByRole("button", { name: "Review / edit", exact: true });
    this.rememberCheckbox = this.root.getByRole("checkbox", { name: /^Remember values for this process/ });
    this.rememberedContainerNote = this.root.getByRole("alert").filter({ hasText: /^The remembered container/ });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel", exact: true });
    this.backButton = this.root.getByRole("button", { name: "Back", exact: true });
    // MUI's StepLabel has no role that carries aria-current, so the attribute is the only hook.
    this.currentStep = this.root.locator('[aria-current="step"]');
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  /** The step label also holds the step's number or a tick, so only its end is matched. */
  async expectStep(step: WizardStep): Promise<void> {
    await expect(this.currentStep).toHaveText(new RegExp(`${step}$`));
  }

  /** Opens the operation, which lands on Details, on Location for a remembered grid, or on Confirm. */
  async chooseOperation(operation: WizardOperation): Promise<void> {
    await this.root.getByRole("button", { name: new RegExp(`^${operation}\\b`) }).click();
    await expect(this.root.getByRole("heading", { level: 2, name: new RegExp(`^${operation}`) })).toBeVisible();
    await expect(this.currentStep.or(this.performButton)).toBeVisible();
  }

  async next(): Promise<void> {
    await this.location.grid.leave();
    const from = await this.currentStep.innerText();
    await this.nextButton.click();
    await expect(this.currentStep).not.toHaveText(from);
  }

  async back(): Promise<void> {
    await this.location.grid.leave();
    const from = await this.currentStep.innerText();
    await this.backButton.click();
    await expect(this.currentStep).not.toHaveText(from);
  }

  async fillProcessName(name: string): Promise<void> {
    await this.root.getByRole("combobox", { name: "Process name" }).fill(name);
  }

  async setCount(count: number): Promise<void> {
    await this.root.getByRole("textbox", { name: "Number of new subsamples" }).fill(String(count));
  }

  /** From Details, through Template (no template), Amounts and Documentation, to Location. */
  async goToLocation(options: { count?: number; processName?: string } = {}): Promise<PlacementStepComponent> {
    await this.expectStep("Details");
    if (options.processName !== undefined) await this.fillProcessName(options.processName);
    await this.next();
    await this.root.getByRole("radio", { name: "No template" }).check();
    await this.next();
    if (options.count !== undefined) await this.setCount(options.count);
    await this.next();
    await this.next();
    await this.expectStep("Location");
    return this.location;
  }

  /** The Confirm step's Location value. */
  async confirmedLocation(): Promise<string> {
    const term = this.root.getByRole("term").filter({ hasText: /^Location$/ });
    await term.waitFor({ state: "visible" });
    return term.evaluate((dt) => {
      let next = dt.nextElementSibling;
      while (next && next.tagName !== "DD") next = next.nextElementSibling;
      return next?.textContent?.trim() ?? "";
    });
  }

  /** The one-click run offered for remembered values: no stepper, only Review / edit and Perform. */
  async expectFastPath(): Promise<void> {
    await expect(this.performButton).toBeVisible();
    await expect(this.reviewEditButton).toBeVisible();
    await expect(this.currentStep).toHaveCount(0);
  }

  async perform(): Promise<void> {
    await this.location.grid.leave();
    await this.performButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.location.grid.leave();
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
