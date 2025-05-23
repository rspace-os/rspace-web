import React, { useRef, useState, useEffect } from "react";
import QrScanner from "qr-scanner";
import HelpTextAlert from "../../../components/HelpTextAlert";
import { mkAlert } from "../../../stores/contexts/Alert";
import useStores from "../../../stores/use-stores";
import BarcodeScannerSkeleton, {
  type BarcodeInput,
} from "./BarcodeScannerSkeleton";

type QrCodeScannerArgs = {
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  buttonPrefix: string;
};

export default function QrCodeScanner({
  onClose,
  onScan,
  buttonPrefix,
}: QrCodeScannerArgs): React.ReactNode {
  const { uiStore } = useStores();
  const [loading, setLoading] = useState<boolean>(true);
  const [barcode, setBarcode] = useState<BarcodeInput | null>(null);
  const [error, setError] = useState(false);

  const videoElem = useRef<HTMLVideoElement | null>(null);

  let qrScanner: QrScanner | undefined;

  async function startScanner(videoEl: HTMLVideoElement) {
    try {
      qrScanner = new QrScanner(
        videoEl,
        (result) => {
          setBarcode({ format: "qr_code", rawValue: result.data });
        },
        {
          onDecodeError: () => {},
        }
      );
      await qrScanner.start();
    } catch (e) {
      if (typeof e === "string")
        uiStore.addAlert(
          mkAlert({
            title: "Unable to start camera with Barcode Detector.",
            message:
              e === "Camera not found."
                ? `If your device has a camera, please reset camera permission, and try again.`
                : e,
            variant: "error",
            isInfinite: true,
          })
        );
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const videoEl = videoElem.current;
    if (videoEl) {
      void startScanner(videoEl);
      return () => qrScanner?.stop();
    }
  }, []);

  return (
    <BarcodeScannerSkeleton
      onClose={onClose}
      onScan={onScan}
      buttonPrefix={buttonPrefix}
      beforeScanHelpText="QR format supported"
      videoElem={videoElem}
      barcode={barcode}
      setBarcode={setBarcode}
      loading={loading}
      warning={
        error ? (
          <HelpTextAlert
            severity="warning"
            condition={!loading}
            text="Could not access camera, please enter code below."
          />
        ) : (
          <HelpTextAlert
            severity="warning"
            condition={!loading && !barcode?.rawValue}
            text={`To scan other formats,
              please try an Android device, or Chrome on a Mac (with a webcam).`}
          />
        )
      }
      error={error}
    />
  );
}
