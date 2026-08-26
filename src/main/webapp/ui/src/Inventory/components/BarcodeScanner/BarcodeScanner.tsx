import { observer } from "mobx-react-lite";
import { Dialog } from "@/components/DialogBoundary";
import AllBarcodeScanner from "./AllBarcodeScanner";
import type { BarcodeInput } from "./BarcodeScannerSkeleton";
import QrCodeScanner from "./QrCodeScanner";

type BarcodeScannerArgs = {
  open: boolean;
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  cameraErrorMessage?: string;
};

/*
 * A centered dialog holding the Barcode Detection API (AllBarcodeScanner)
 * where available, or else the qr-scanner library.
 */
function BarcodeScanner({ open, onClose, onScan, cameraErrorMessage }: BarcodeScannerArgs) {
  const barcodeDetectorApiSupported = "BarcodeDetector" in window;

  return (
    <Dialog open={open} onClose={onClose}>
      {barcodeDetectorApiSupported ? (
        <AllBarcodeScanner onClose={onClose} onScan={onScan} cameraErrorMessage={cameraErrorMessage} />
      ) : (
        <QrCodeScanner onClose={onClose} onScan={onScan} cameraErrorMessage={cameraErrorMessage} />
      )}
    </Dialog>
  );
}

export default observer(BarcodeScanner);
