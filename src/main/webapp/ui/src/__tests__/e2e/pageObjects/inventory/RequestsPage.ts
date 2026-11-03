import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

/**
 * The Requests page at /inventory/requests: a list of the viewer's own and
 * owned-sample requests on the left, and the selected request's details on
 * the right. Reached from the Inventory sidebar's "Requests" item, which
 * only appears once a sysadmin has set sampleRequests.available to ALLOWED.
 */
export class RequestsPage extends BasePage {
  readonly path = "/inventory/requests";

  readonly root: Locator;
  readonly noRequestsMessage: Locator;
  readonly heading: Locator;
  /**
   * Owner-only: opens the Transfer Ownership dialog directly for the currently selected
   * subsample. Labelled "Transfer Sample" rather than "Prepare Sample" because
   * inventory.operations.available is DENIED, making RequestDetailPanel's `skipChooseMethodDialog`
   * true - the Operations Wizard this branch would otherwise offer isn't present here at all, so
   * every spec that reaches this button pairs `flowSampleRequestsAvailable` with
   * `flowOperationsDenied` to guarantee that, rather than relying on whatever this property
   * happens to be left as on a given instance.
   */
  readonly transferSampleButton: Locator;

  constructor(page: Page) {
    super(page);
    this.root = page.getByRole("main", { name: "Requests" });
    this.noRequestsMessage = this.root.getByText("No requests found.", { exact: true });
    this.heading = this.root.getByRole("heading", { level: 5 });
    this.transferSampleButton = this.root.getByRole("button", { name: "Transfer Sample", exact: true });
  }

  /** Owner-only: selects the first available subsample in the Sample Locations section. */
  async selectFirstAvailableSubsample(): Promise<void> {
    await this.root.getByRole("radio").first().check();
  }

  async isLoaded(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  /** Deep-links straight to one request, as the status chip in a Sample's own page does. */
  async openRequest(requestId: number): Promise<void> {
    await this.page.goto(`${this.path}?requestId=${requestId}`);
    await this.heading.waitFor({ state: "visible" });
  }

  /**
   * The request's current status chip, next to its heading. Scoped with `.first()`: the same
   * status text can also appear in the Request History table further down the page, and the
   * heading's own chip is always the first such match in DOM order.
   */
  statusChip(status: string): Locator {
    return this.root.getByText(status, { exact: true }).first();
  }

  /** One of the labelled detail fields (e.g. "Notes from requester"), heading included. */
  detailField(label: string): Locator {
    return this.root.getByRole("group", { name: label });
  }

  /** Requester-only: cancels the currently open (PENDING) request from its own Actions section. */
  async cancelRequest(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.statusChip("Cancelled").waitFor({ state: "visible" });
  }

  /** Owner-only: rejects the currently open request, giving the required reason. */
  async rejectRequest(reason: string): Promise<void> {
    await this.root.getByRole("button", { name: "Reject", exact: true }).click();
    const dialog = this.page.getByRole("dialog", { name: "Confirm request rejection" });
    // The reason field's label is a plain Typography (no htmlFor), so it isn't a real <label>;
    // it's the only textbox in this dialog, so a bare role query still finds it unambiguously.
    await dialog.getByRole("textbox").fill(reason);
    await dialog.getByRole("button", { name: "Reject Request", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }

  /** Owner-only: approves the currently open (PENDING) request. No confirmation dialog. */
  async approveRequest(): Promise<void> {
    await this.root.getByRole("button", { name: "Approve", exact: true }).click();
    await this.statusChip("Approved").waitFor({ state: "visible" });
  }

  /** Owner-only: marks the currently open (APPROVED) request as fulfilled, without a transfer. */
  async markAsFulfilled(): Promise<void> {
    await this.root.getByRole("button", { name: "Mark as Fulfilled", exact: true }).click();
    const dialog = this.page.getByRole("dialog");
    await dialog.getByRole("button", { name: "Fulfil", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }
}
