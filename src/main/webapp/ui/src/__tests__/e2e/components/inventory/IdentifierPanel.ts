import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached, dismissMobileDatePickerIfPresent } from "./DialogHelpers";

export type IdentifierDescriptionType = "Abstract" | "Methods";

export class IdentifierCreateDialog {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page
      .getByRole("dialog")
      .filter({ has: page.getByRole("heading", { name: "You are about to create an Identifier", exact: true }) });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async confirm(identifiers: IdentifierPanel): Promise<void> {
    await clickAndWaitDetached(this.root.getByRole("button", { name: "OK", exact: true }), this.root);
    await identifiers.waitForVisible();
  }
}

export class IdentifierPanel {
  readonly root: Locator;
  private readonly previewButton: Locator;
  private readonly publishButton: Locator;
  private readonly retractButton: Locator;
  private readonly deleteButton: Locator;

  constructor(
    private readonly page: Page,
    section: Locator,
  ) {
    this.root = section;
    this.previewButton = this.root.getByRole("button", { name: "Preview", exact: true });
    this.publishButton = this.root.getByRole("button", { name: "Publish", exact: true });
    this.retractButton = this.root.getByRole("button", { name: "Retract", exact: true });
    this.deleteButton = this.root.getByRole("button", { name: "Delete", exact: true });
  }

  async waitForVisible(): Promise<void> {
    await this.previewButton.waitFor({ state: "visible" });
  }

  async waitForState(state: string): Promise<void> {
    try {
      await this.root.getByText(state, { exact: true }).waitFor({ state: "visible", timeout: 5000 });
    } catch {
      await this.page.reload();
      await this.ensureExpanded();
      await this.root.getByText(state, { exact: true }).waitFor({ state: "visible" });
    }
  }

  private async ensureExpanded(): Promise<void> {
    const expandButton = this.root.getByRole("button", { name: "Expand section" });
    if (await expandButton.isVisible().catch(() => false)) {
      await expandButton.click();
    }
  }

  async addSubject(data: {
    subject: string;
    schema: string;
    schemaUri: string;
    valueUri: string;
    code: string;
  }): Promise<void> {
    await this.root.getByRole("group", { name: "Subjects" }).getByRole("button", { name: "Add another value" }).click();
    await this.root.getByPlaceholder("Enter value for new Subject").fill(data.subject);
    await this.root.getByPlaceholder("Enter value for Subject Scheme").fill(data.schema);
    await this.root.getByPlaceholder("Enter value for Scheme URI").fill(data.schemaUri);
    await this.root.getByPlaceholder("Enter value for Value URI").fill(data.valueUri);
    await this.root.getByPlaceholder("Enter value for Classification Code").fill(data.code);
  }

  async addDescription(type: IdentifierDescriptionType, description: string): Promise<void> {
    await this.root
      .getByRole("group", { name: "Descriptions" })
      .getByRole("button", { name: "Add another value" })
      .click();
    await this.root.getByPlaceholder("Enter value for new Description").fill(description);
    await this.root.getByLabel("Description Type").click();
    await this.page.getByRole("option", { name: type }).click();
  }

  async addDateAndEventType(day: string, eventType: string): Promise<void> {
    await this.root.getByRole("group", { name: "Dates" }).getByRole("button", { name: "Add another value" }).click();
    await this.root.getByRole("group", { name: "Dates" }).getByLabel("Choose date").click();
    const datePicker = this.page.getByRole("dialog").last();
    await datePicker.getByRole("gridcell", { name: day, exact: true }).click();
    await dismissMobileDatePickerIfPresent(datePicker);
    await this.page.getByLabel("Event Type").click();
    await this.page.getByRole("option", { name: eventType, exact: true }).click();
  }

  get subjects(): Locator {
    return this.page.getByRole("group", { name: "Subjects" });
  }

  async clickPreview(): Promise<void> {
    await this.ensureExpanded();
    await this.previewButton.click();
    await this.page.getByRole("heading", { name: "Review your page before publishing", exact: true }).waitFor();
  }

  async clickPublish(): Promise<void> {
    await this.ensureExpanded();
    await this.publishButton.click();
    await this.confirm("You are about to publish this Identifier");
  }

  async clickRetract(): Promise<void> {
    await this.ensureExpanded();
    await this.retractButton.click();
    await this.confirm("You are about to retract this Identifier");
  }

  async clickDelete(): Promise<void> {
    await this.ensureExpanded();
    await this.deleteButton.click();
    await this.confirm("You are about to delete this Identifier");
  }

  private async confirm(title: string): Promise<void> {
    const dialog = this.page
      .getByRole("dialog")
      .filter({ has: this.page.getByRole("heading", { name: title, exact: true }) });
    await dialog.getByRole("button", { name: "OK", exact: true }).click();
  }
}
