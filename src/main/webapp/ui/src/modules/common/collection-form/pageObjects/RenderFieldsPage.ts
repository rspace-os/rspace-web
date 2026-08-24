import { type Locator, page, userEvent } from "vitest/browser";

export class RenderFieldsPage {
  readonly smallContainer: Locator = page.getByRole("region", { name: "Small form container" });
  readonly recordDetailsSection: Locator = page.getByRole("group", { name: /record details/i });
  readonly relationshipsSection: Locator = page.getByRole("group", { name: /relationships/i });
  readonly title: Locator = page.getByRole("textbox", { name: "Title" });
  readonly notes: Locator = page.getByRole("textbox", { name: "Notes" });
  readonly score: Locator = page.getByRole("spinbutton", { name: "Score" });
  readonly enabled: Locator = page.getByRole("checkbox", { name: "Enabled" });
  readonly modifiedAt: Locator = page.getByLabelText("Modified");
  readonly status: Locator = page.getByRole("combobox", { name: "Status" });
  readonly cardStatusGroup: Locator = page.getByRole("radiogroup", { name: "Status" });
  readonly constantStatusOption: Locator = page.getByRole("option", { name: "draft" });
  readonly objectStatusOption: Locator = page.getByRole("option", { name: "In review" });
  readonly richStatusOption: Locator = page.getByText("Ready to publish");
  readonly owner: Locator = page.getByRole("combobox", { name: "Owner" });
  readonly collaborators: Locator = page.getByRole("combobox", { name: "Collaborators" });
  readonly richRelationshipContent: Locator = page.getByText("Rear admiral and computer scientist");
  readonly hiddenId: Locator = page.getByRole("textbox", { name: "ID" });
  readonly values: Locator = page.getByRole("status", { name: "Form values" });

  async setTitle(value: string): Promise<void> {
    await userEvent.fill(this.title, value);
  }

  async setNotes(value: string): Promise<void> {
    await userEvent.fill(this.notes, value);
  }

  async setScore(value: string): Promise<void> {
    await userEvent.fill(this.score, value);
  }

  async toggleEnabled(): Promise<void> {
    await userEvent.click(this.enabled);
  }

  async chooseStatus(label: string): Promise<void> {
    await userEvent.click(this.status);
    await userEvent.click(page.getByRole("option", { name: label }));
  }

  cardStatus(label: string): Locator {
    return page.getByRole("radio", { name: label });
  }

  async chooseCardStatus(label: string): Promise<void> {
    await userEvent.click(this.cardStatus(label));
  }

  async chooseNextCardStatus(from: string): Promise<void> {
    await userEvent.click(this.cardStatus(from));
    await userEvent.keyboard("{ArrowRight}");
  }

  async openStatus(): Promise<void> {
    await userEvent.click(this.status);
  }

  async typeStatus(value: string): Promise<void> {
    await userEvent.fill(this.status, value);
  }

  async chooseOwner(label: string): Promise<void> {
    await userEvent.click(this.owner);
    await userEvent.click(page.getByRole("option", { name: label }));
  }

  async openOwner(): Promise<void> {
    await userEvent.click(this.owner);
  }

  async addCollaborator(label: string): Promise<void> {
    await userEvent.click(this.collaborators);
    await userEvent.click(page.getByRole("option", { name: label }));
    await userEvent.keyboard("{Escape}");
  }

  async removeCollaborator(label: string): Promise<void> {
    await userEvent.click(page.getByRole("button", { name: `Remove ${label} from Collaborators` }));
  }
}
