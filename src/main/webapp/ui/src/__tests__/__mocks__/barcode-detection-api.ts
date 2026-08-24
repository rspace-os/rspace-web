import type { Barcode, BarcodeFormat } from "../../util/barcode";

let mockScannedValue = "foo";

/** Set the rawValue the mock BarcodeDetector reports on its next detect(). */
export function setMockScannedBarcode(rawValue: string): void {
  mockScannedValue = rawValue;
}

class BarcodeDetector {
  detect(): Promise<Array<Barcode>> {
    return Promise.resolve([
      {
        rawValue: mockScannedValue,
        format: "qr_code",
      },
    ]);
  }

  getSupportedFormats(): Promise<Array<BarcodeFormat>> {
    return Promise.reject();
  }
}

Object.defineProperty(window, "BarcodeDetector", {
  writable: false,
  value: BarcodeDetector,
});
