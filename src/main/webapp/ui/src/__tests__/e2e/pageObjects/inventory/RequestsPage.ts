import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class RequestsPage extends BasePage {
  readonly path = "/inventory/requests";

  private readonly root: Locator;
  readonly heading: Locator;
  readonly prepareSampleButton: Locator;

  constructor(page: Page) {
    super(page);
    this.root = page.getByRole("main", { name: "Requests" });
    this.heading = this.root.getByRole("heading", { level: 5 });
    this.prepareSampleButton = this.root.getByRole("button", { name: "Prepare Sample", exact: true });
  }

  /** Deep-links straight to one request, as the status chip in a Sample's own page does. */
  async openRequest(requestId: number): Promise<void> {
    await this.page.goto(`${this.path}?requestId=${requestId}`);
    await this.heading.waitFor({ state: "visible" });
  }

  /**
   * The request's current status chip, next to its heading. Scoped with `.first()`: the same
   * status text can also appear in the Request History table further down the page (e.g. the
   * PENDING entry that's still there after a later transition), and the heading's chip is always
   * the first such match in DOM order.
   */
  statusChip(status: string): Locator {
    return this.root.getByText(status, { exact: true }).first();
  }

  /** One of the labelled detail fields (e.g. "Additional notes", "Requester"), heading included. */
  detailField(label: string): Locator {
    return this.root.getByRole("group", { name: label });
  }

  /** Owner-only: rejects the currently open request, giving the required reason. */
  async rejectRequest(reason: string): Promise<void> {
    await this.root.getByRole("button", { name: "Reject", exact: true }).click();
    const dialog = this.page.getByRole("dialog", { name: "Confirm request rejection" });
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

  /** Owner-only: selects the first available subsample in the Sample Locations section. */
  async selectFirstAvailableSubsample(): Promise<void> {
    await this.root.getByRole("radio").first().check();
  }

  /** The dialog opened by the "Prepare Sample" button, for picking how to prepare the sample. */
  chooseMethodDialog(): Locator {
    return this.page.getByRole("dialog", { name: "Choose Sample to Prepare" });
  }

  /** The dialog that follows a Prepare Sample action, for transferring the resulting sample. */
  transferDialog(): Locator {
    return this.page.getByRole("dialog", { name: "Transfer Ownership" });
  }

  /**
   * Owner-only: opens the Choose Sample to Prepare dialog, picks "Transfer the existing sample
   * and all subsamples", proceeds, and confirms the transfer - fulfilling this request with the
   * originally requested sample rather than a wizard-derived one.
   */
  async prepareAndTransferSample(): Promise<void> {
    await this.prepareSampleButton.click();
    const chooseDialog = this.chooseMethodDialog();
    await chooseDialog
      .getByRole("radio", { name: "Transfer the existing sample and all subsamples", exact: true })
      .check();
    await chooseDialog.getByRole("button", { name: "Proceed", exact: true }).click();

    await this.confirmTransfer();
  }

  /**
   * Owner-only: opens the Choose Sample to Prepare dialog, picks "Create a new sample derived
   * from the existing sample", runs the Aliquot operation through the full Operations Wizard
   * (no template, default amounts/documentation), then confirms the transfer of the resulting
   * sample to the requester. Returns the new sample's global id.
   */
  async performAliquotAndTransfer(newSampleName: string): Promise<{ globalId: string }> {
    const result = await this.runAliquotOperation(newSampleName);
    await this.confirmTransfer();
    return result;
  }

  /**
   * As {@link performAliquotAndTransfer}, but cancels the resulting Transfer Ownership dialog
   * instead of confirming it: the new sample is created and stays with its current owner, and
   * the request itself is untouched (fulfilling it only happens as part of confirming a transfer).
   */
  async performAliquotWithoutTransferring(newSampleName: string): Promise<{ globalId: string }> {
    const result = await this.runAliquotOperation(newSampleName);
    await this.cancelTransfer();
    return result;
  }

  /**
   * Runs the Aliquot operation through the full Operations Wizard (no template, default
   * amounts/documentation) and leaves the resulting Transfer Ownership dialog open for the
   * caller to confirm or cancel. Returns the new sample's global id.
   */
  private async runAliquotOperation(newSampleName: string): Promise<{ globalId: string }> {
    await this.prepareSampleButton.click();
    const chooseDialog = this.chooseMethodDialog();
    await chooseDialog
      .getByRole("radio", { name: "Create a new sample derived from the existing sample", exact: true })
      .check();
    await chooseDialog.getByRole("button", { name: "Proceed", exact: true }).click();

    const pickerDialog = this.page.getByRole("dialog", { name: "Process subsample" });
    // Not exact: the picker's accessible name for each operation includes its description text
    // too (e.g. "Aliquot Take equal-volume aliquots..."), so match the operation name as a prefix.
    await pickerDialog.getByRole("button", { name: "Aliquot" }).click();

    const wizardDialog = this.page.getByRole("dialog", { name: "Aliquot" });
    const next = () => wizardDialog.getByRole("button", { name: "Next", exact: true }).click();

    await wizardDialog.getByLabel("New sample name").fill(newSampleName);
    await next(); // details -> template

    await wizardDialog.getByRole("radio", { name: "No template", exact: true }).check();
    await next(); // template -> amounts

    await next(); // amounts -> documentation
    await next(); // documentation -> confirm

    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/operations/aliquot") && res.request().method() === "POST",
      ),
      wizardDialog.getByRole("button", { name: "Perform", exact: true }).click(),
    ]);
    await wizardDialog.waitFor({ state: "hidden" });

    const body = (await response.json()) as { sample: { globalId: string } | null };
    if (!body.sample) throw new Error("Aliquot operation did not return a created sample");
    return { globalId: body.sample.globalId };
  }

  /** Confirms the Transfer Ownership dialog that follows a Prepare Sample action. */
  private async confirmTransfer(): Promise<void> {
    const transferDialog = this.transferDialog();
    await transferDialog.getByRole("button", { name: "Transfer", exact: true }).click();
    await transferDialog.waitFor({ state: "hidden" });
  }

  /** Cancels the Transfer Ownership dialog that follows a Prepare Sample action. */
  private async cancelTransfer(): Promise<void> {
    const transferDialog = this.transferDialog();
    await transferDialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await transferDialog.waitFor({ state: "hidden" });
  }
}
