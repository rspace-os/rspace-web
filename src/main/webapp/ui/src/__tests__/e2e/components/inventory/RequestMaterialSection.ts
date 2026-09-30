import type { Locator, Page } from "@playwright/test";

/**
 * The "Request this sample" box in a Sample's Overview section (non-owners
 * only): a "Request Sample" button that opens a note dialog, and, once a
 * request has been sent, its status chip alongside a "Cancel" button.
 */
export class RequestMaterialSection {
  private readonly requestButton: Locator;
  private readonly cancelButton: Locator;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.requestButton = this.root.getByRole("button", { name: "Request Sample", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel", exact: true });
  }

  /** Opens the request dialog, submits it with the given note, and returns the created request's id. */
  async sendRequest(note: string): Promise<number> {
    await this.requestButton.click();
    await this.page.getByLabel("Describe your request").fill(note);
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/sampleRequests") && res.request().method() === "POST",
      ),
      this.page.getByRole("button", { name: "Send Request", exact: true }).click(),
    ]);
    await this.cancelButton.waitFor({ state: "visible" });
    const body = (await response.json()) as { id: number };
    return body.id;
  }

  /** Only legal while the request is PENDING, matching the button's own gating. */
  async cancelRequest(): Promise<void> {
    await this.cancelButton.click();
    await this.requestButton.waitFor({ state: "visible" });
  }

  /** The existing request's status chip (e.g. "Pending", "Cancelled"). */
  statusChip(status: string): Locator {
    return this.root.getByText(status, { exact: true });
  }
}
