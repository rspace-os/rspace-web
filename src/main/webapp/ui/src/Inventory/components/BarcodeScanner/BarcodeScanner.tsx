import { observer } from "mobx-react-lite";
import AllBarcodeScanner from "./AllBarcodeScanner";
import type { BarcodeInput } from "./BarcodeScannerSkeleton";
import QrCodeScanner from "./QrCodeScanner";

type BarcodeScannerArgs = {
    onClose: () => void;
    onScan: (scannedBarcodeInput: BarcodeInput) => void;
    buttonPrefix: string;
};

/*
 * This component encapsulates the use of the Barcode Detection API
 * (AllBarcodeScanner) where available or else the qr-scanner library.
 */
function BarcodeScanner({ onClose, onScan, buttonPrefix }: BarcodeScannerArgs) {
    const barcodeDetectorApiSupported = "BarcodeDetector" in window;

    return barcodeDetectorApiSupported ? (
        <AllBarcodeScanner onClose={onClose} onScan={onScan} buttonPrefix={buttonPrefix} />
    ) : (
        <QrCodeScanner onClose={onClose} onScan={onScan} buttonPrefix={buttonPrefix} />
    );
}

export default observer(BarcodeScanner);
