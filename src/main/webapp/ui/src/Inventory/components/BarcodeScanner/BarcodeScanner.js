//@flow

import React, { type ComponentType } from "react";
import QrCodeScanner from "./QrCodeScanner";
import AllBarcodeScanner from "./AllBarcodeScanner";
import { observer } from "mobx-react-lite";
import { type BarcodeInput } from "./BarcodeScannerSkeleton";

type BarcodeScannerArgs = {|
  onClose: () => void,
  onScan: (BarcodeInput) => void,
  buttonPrefix: string,
|};

/*
 * This component encapsulates the use of the Barcode Detection API
 * (AllBarcodeScanner) where available or else the qr-scanner library.
 */
function BarcodeScanner({ onClose, onScan, buttonPrefix }: BarcodeScannerArgs) {
  const barcodeDetectorApiSupported = "BarcodeDetector" in window;

  return barcodeDetectorApiSupported ? (
    <AllBarcodeScanner
      onClose={onClose}
      onScan={onScan}
      buttonPrefix={buttonPrefix}
    />
  ) : (
    <QrCodeScanner
      onClose={onClose}
      onScan={onScan}
      buttonPrefix={buttonPrefix}
    />
  );
}

export default (observer(BarcodeScanner): ComponentType<BarcodeScannerArgs>);
