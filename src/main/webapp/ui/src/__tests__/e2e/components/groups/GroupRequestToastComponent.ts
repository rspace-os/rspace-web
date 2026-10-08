import type { Locator, Page } from "@playwright/test";

/** Legacy toast offering to join, or be PI of, the named group. */
export class GroupRequestToastComponent {
  readonly root: Locator;

  constructor(
    private readonly page: Page,
    groupName: string,
  ) {
    this.root = page.locator(".toast-item").filter({ hasText: `'${groupName}'` });
  }

  async accept(): Promise<void> {
    await this.respond("Accept");
  }

  async decline(): Promise<void> {
    await this.respond("Decline");
  }

  // A failed reply leaves the toast up, so surface the server's answer instead of timing out on it.
  private async respond(button: "Accept" | "Decline"): Promise<void> {
    const response = this.page.waitForResponse(
      (res) => res.request().method() === "POST" && new URL(res.url()).pathname === "/dashboard/ajax/messageStatus",
    );
    await this.root.getByRole("button", { name: button, exact: true }).click();
    const res = await response;
    const body = (await res.json().catch(() => undefined)) as { data?: string } | undefined;
    if (!res.ok() || body?.data !== "Success") {
      throw new Error(`${button} group request failed: HTTP ${res.status()} ${JSON.stringify(body)}`);
    }
    await this.root.waitFor({ state: "hidden" });
  }
}
