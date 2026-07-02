import type { Locator, Page } from "@playwright/test";

export type CreateMenuItem =
  | "Folder"
  | "Notebook"
  | "Basic Document"
  | "From Form"
  | "From Template"
  | "From Evernote"
  | "From Protocols.io"
  | "New Form";

const CREATE_TEST_ID: Record<CreateMenuItem, string> = {
  Folder: "create-btn-folder",
  Notebook: "create-btn-notebook",
  "Basic Document": "create-btn-basic-document",
  "From Form": "create-btn-from-form",
  "From Template": "create-btn-template",
  "From Evernote": "create-btn-evernote",
  "From Protocols.io": "create-btn-protocols",
  "New Form": "create-btn-new-form",
};

export class ToolbarCreateMenu {
  readonly createButton: Locator;

  constructor(private readonly page: Page) {
    this.createButton = page.getByTestId("create-btn");
  }

  async create(item: CreateMenuItem): Promise<void> {
    await this.createButton.click();
    await this.page.getByTestId(CREATE_TEST_ID[item]).click();
  }

  /**
   * Creates from a custom form by its exact display name (e.g. "Experiment",
   * "Selenium"). derives the test ID withm`.replace(" ", "-")` a non-global replace,
   * so only the *first* space becomes a hyphen ("RSpace Tags from Ontologies" →
   * `create-btn-rspace-tags from ontologies`).
   */
  async createFromCustomForm(name: string): Promise<void> {
    await this.createButton.click();
    const testId = `create-btn-${name.toLowerCase().replace(" ", "-")}`;
    await this.page.getByTestId(testId).click();
  }
}
