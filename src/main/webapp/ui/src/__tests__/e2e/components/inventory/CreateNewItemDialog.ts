import type { Locator, Page } from "@playwright/test";
import { openDialog } from "./DialogHelpers";
import { type NewContainerFormComponent, newContainerFormComponent } from "./NewContainerFormComponent";
import { type NewSampleFormComponent, newSampleFormComponent } from "./NewSampleFormComponent";

export class CreateNewItemDialog {
  readonly root: Locator;
  readonly sampleOption: Locator;
  readonly containerOption: Locator;
  private readonly newSubsamplesRadio: Locator;
  private readonly splitSampleRadio: Locator;
  private readonly templateRadio: Locator;
  private readonly createButton: Locator;
  private readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Create new items from" });
    this.sampleOption = this.root.getByRole("radio", { name: "Sample", exact: true });
    this.containerOption = this.root.getByRole("radio", { name: "Container", exact: true });
    this.newSubsamplesRadio = this.root.getByRole("radio", { name: "Subsamples, by creating new ones" });
    this.splitSampleRadio = this.root.getByRole("radio", {
      name: "Subsamples, by splitting the existing subsample",
    });
    this.templateRadio = this.root.getByRole("radio", { name: "Template", exact: true });
    this.createButton = this.root.getByRole("button", { name: "Create", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectLocation(position: number): Promise<void> {
    await this.root.getByRole("gridcell", { name: String(position), exact: true }).click();
  }

  async chooseSample(options?: { location?: number }): Promise<NewSampleFormComponent> {
    return openDialog(async () => {
      await this.sampleOption.click();
      if (options?.location !== undefined) {
        await this.selectLocation(options.location);
      }
      await this.createButton.click();
    }, newSampleFormComponent(this.page));
  }

  async chooseContainer(options?: { location?: number }): Promise<NewContainerFormComponent> {
    return openDialog(async () => {
      await this.containerOption.click();
      if (options?.location !== undefined) {
        await this.selectLocation(options.location);
      }
      await this.createButton.click();
    }, newContainerFormComponent(this.page));
  }

  newSubsamplesOption(): Locator {
    return this.newSubsamplesRadio;
  }

  splitSampleOption(): Locator {
    return this.splitSampleRadio;
  }

  templateOption(): Locator {
    return this.templateRadio;
  }

  async splitIntoSubsamples(totalCount: number): Promise<void> {
    await this.splitSampleRadio.click();
    await this.root.getByRole("spinbutton", { name: "Number of new subsamples" }).fill(String(totalCount));
    await this.createButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async createTemplate(name: string): Promise<void> {
    await this.templateRadio.click();
    await this.root.getByRole("textbox", { name: "Name" }).fill(name);
    await this.root.getByRole("button", { name: "Next" }).click();
    await this.createButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
