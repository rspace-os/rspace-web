//@flow

/*
 * The Barcode Detection API is still an experimental browser API and as such
 * Flow.js does not yet have native support. Therefore, it is necessary for us to
 * create a global library definition.
 */

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

export type BarcodeValue = string;

export type Barcode = {|
  format: BarcodeFormat,
  rawValue: BarcodeValue,
|};

declare class BarcodeDetector {
  constructor({ formats: Array<BarcodeFormat> }): BarcodeDetector;
  detect(
    HTMLVideoElement | HTMLImageElement | Blob | ImageData
  ): Promise<Array<Barcode>>;
  getSupportedFormats(): Promise<Array<BarcodeFormat>>;
}
