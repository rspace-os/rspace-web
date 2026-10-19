import type { Locator, Page } from "@playwright/test";

export type PublicationKind = "internet" | "link";

const TAB_NAME: Record<PublicationKind, string> = {
  internet: "Publish on the internet",
  link: "Publish a link",
};

export class PublishDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Publish" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async publish(kind: PublicationKind, summary: string, displayContactDetails: boolean): Promise<void> {
    await this.root.getByRole("tab", { name: TAB_NAME[kind], exact: true }).click();
    const panel = this.root.getByRole("tabpanel", { name: TAB_NAME[kind], exact: true });
    await panel.getByRole("textbox", { name: "Summary description, max length 200 characters:" }).fill(summary);
    await panel.getByRole("checkbox", { name: "Display contact details?" }).setChecked(displayContactDetails);
    await panel.getByRole("textbox").last().fill("confirm");
    const [response] = await Promise.all([
      this.root.page().waitForResponse((res) => res.url().includes("/ajax/shareRecord")),
      this.root.getByRole("button", { name: "Publish", exact: true }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Publish request failed with HTTP ${response.status()}: ${await response.text()}`);
    }

    const body: { errorMsg?: { errorMessages?: string[] }; data?: { error?: { errorMessages?: string[] } } } =
      await response.json();
    const nestedErrors = body.data?.error?.errorMessages ?? [];
    if ((body.errorMsg?.errorMessages && body.errorMsg.errorMessages.length > 0) || nestedErrors.length > 0) {
      throw new Error(`Publish failed: ${JSON.stringify(nestedErrors.length > 0 ? nestedErrors : body.errorMsg)}`);
    }
    await this.root.waitFor({ state: "hidden" });
  }
}
