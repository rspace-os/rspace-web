import Dialog from "@mui/material/Dialog";
import { observer } from "mobx-react-lite";
import AllBarcodeScanner from "./AllBarcodeScanner";
import { BARCODE_SCANNER_TITLE_ID, type BarcodeInput } from "./BarcodeScannerSkeleton";
import QrCodeScanner from "./QrCodeScanner";

type BarcodeScannerArgs = {
  open: boolean;
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  cameraErrorMessage?: string;
};

/*
 * A centered dialog holding the Barcode Detection API (AllBarcodeScanner)
 * where available, or else the qr-scanner library. The dialog is not anchored
 * to the button that opened it because the video only grows once the camera
 * starts, which would overflow a popover positioned before that.
 */
function BarcodeScanner({ open, onClose, onScan, cameraErrorMessage }: BarcodeScannerArgs) {
  const barcodeDetectorApiSupported = "BarcodeDetector" in window;

  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={BARCODE_SCANNER_TITLE_ID}>
      {barcodeDetectorApiSupported ? (
        <AllBarcodeScanner onClose={onClose} onScan={onScan} cameraErrorMessage={cameraErrorMessage} />
      ) : (
        <QrCodeScanner onClose={onClose} onScan={onScan} cameraErrorMessage={cameraErrorMessage} />
      )}
    </Dialog>
  );
}

export default observer(BarcodeScanner);
