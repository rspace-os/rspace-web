import type { Locator } from "@playwright/test";

export class NotesEditor {
  constructor(private readonly root: Locator) {}

  async addNote(text: string): Promise<void> {
    const paragraph = this.root
      .getByRole("group", { name: "New note" })
      .locator("iframe")
      .contentFrame()
      .getByRole("paragraph");
    await paragraph.click();
    await paragraph.fill(text);
    await this.root.getByRole("button", { name: "Create note" }).click();
    await this.noteContent(text).waitFor({ state: "visible" });
  }

  noteContent(text: string): Locator {
    return this.root.getByText(text, { exact: true });
  }
}
