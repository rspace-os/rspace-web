import type { Barcode, BarcodeFormat } from "../src/util/barcode";

class BarcodeDetector {
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
  writeable: false,
  value: BarcodeDetector,
});
