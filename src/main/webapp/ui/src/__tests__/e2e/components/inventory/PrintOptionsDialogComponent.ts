import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export type PrintIdentifierType = "Global ID" | "IGSN ID";
export type PrintPrinterType = "Standard Printer" | "Label Printer";
export type PrintLayout = "Full" | "Basic";
export type PrintCopies = "Each barcode once" | "Each barcode twice (raffle book)";
export type PrintSize = "Large" | "Small";

export class PrintOptionsDialogComponent {
  readonly root: Locator;
  private readonly printButton: Locator;
  private readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Print Options" });
    this.printButton = this.root.getByRole("button", { name: "Print selected" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private async selectRadio(name: string, exact = false): Promise<void> {
    await this.root.getByRole("radio", { name, exact }).click();
  }

  async selectIdentifierType(type: PrintIdentifierType): Promise<void> {
    await this.selectRadio(type);
  }

  async selectPrinterType(type: PrintPrinterType): Promise<void> {
    await this.selectRadio(type);
  }

  async selectPrintLayout(layout: PrintLayout): Promise<void> {
    await this.selectRadio(layout, true);
  }

  async selectPrintCopies(copies: PrintCopies): Promise<void> {
    await this.selectRadio(copies);
  }

  async selectPrintSize(size: PrintSize): Promise<void> {
    await this.selectRadio(size, true);
  }

  radio(name: string): Locator {
    return this.root.getByRole("radio", { name });
  }

  message(substring: string): Locator {
    return this.root.getByRole("alert").filter({ hasText: substring });
  }

  get barcodePreview(): Locator {
    return this.root.getByText("Preview Barcode Label Layout");
  }

  async printButtonText(): Promise<string> {
    return (await this.printButton.innerText()).trim();
  }

  async print(): Promise<void> {
    await this.printButton.click();
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
