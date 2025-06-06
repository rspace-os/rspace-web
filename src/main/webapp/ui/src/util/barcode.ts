/* eslint-disable camelcase */

export type BarcodeFormat =
  | "aztec"
  | "code_128"
  | "code_39"
  | "code_93"
  | "codabar"
  | "data_matrix"
  | "ean_13"
  | "ean_8"
  | "itf"
  | "pdf417"
  | "qr_code"
  | "upc_a"
  | "upc_e";

export type Barcode = {
  format: BarcodeFormat;
  rawValue: string;
};

/**
 * Given a barcode format, return a human-readable string.
 */
export function barcodeFormatAsString(
  barcodeFormat: BarcodeFormat | "Unknown"
): string {
  return {
    aztec: "Aztec",
    code_128: "Code 128",
    code_39: "Code 39",
    code_93: "Code 93",
    codabar: "Codabar",
    data_matrix: "Data Matrix",
    ean_13: "EAN-13",
    ean_8: "EAN-8",
    itf: "ITF",
    pdf417: "PDF417",
    qr_code: "QR Code",
    upc_a: "UPC-A",
    upc_e: "UPC-E",
    Unknown: "Unknown",
  }[barcodeFormat];
}
