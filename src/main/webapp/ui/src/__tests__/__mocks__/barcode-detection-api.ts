import type { Barcode, BarcodeFormat } from "../../util/barcode";

class BarcodeDetector {
  // biome-ignore lint/complexity/noUselessConstructor: initial biome migration
  constructor() {}

  detect(): Promise<Array<Barcode>> {
    return Promise.resolve([
      {
        rawValue: "foo",
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
