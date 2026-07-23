import type { Locator, Page } from "@playwright/test";

export type ToastVariant = "success" | "error" | "warning" | "notice";

export class ToastsComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("region", {
      name: "alerts.",
      includeHidden: true,
    });
  }

  byVariant(variant: ToastVariant, messageSubstring: string): Locator {
    return this.root
      .getByRole("group", { name: `${variant} alert` })
      .filter({ hasText: messageSubstring })
      .last();
  }

  byText(messageSubstring: string): Locator {
    return this.root.getByText(messageSubstring).last();
  }

  async expandSubMessages(toast: Locator): Promise<void> {
    await toast.getByRole("button", { name: "sub-messages. Toggle to show" }).click();
  }

  async clickAction(toast: Locator, actionLabel: string): Promise<void> {
    await toast.getByRole("button", { name: actionLabel }).click();
  }

  async dismissAll(): Promise<void> {
    const dismissButtons = this.root.getByRole("button", { name: "Dismiss" });
    while ((await dismissButtons.count()) > 0) {
      await dismissButtons.first().click();
    }
  }
}
