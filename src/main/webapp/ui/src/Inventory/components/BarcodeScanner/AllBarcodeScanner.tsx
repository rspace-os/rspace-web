import React, { useRef, useState, useEffect } from "react";
import useStores from "../../../stores/use-stores";
import { mkAlert } from "../../../stores/contexts/Alert";
import HelpTextAlert from "../../../components/HelpTextAlert";
import BarcodeScannerSkeleton, {
  type BarcodeInput,
} from "./BarcodeScannerSkeleton";
import { doNotAwait } from "../../../util/Util";
import { BarcodeFormat, type Barcode } from "../../../util/barcode";

// scan once per second
const SCAN_INTERVAL = 1000;

const supportedFormats: Array<BarcodeFormat> = [
  "aztec",
  "code_128",
  "code_39",
  "code_93",
  "codabar",
  "data_matrix",
  "ean_13",
  "ean_8",
  "itf",
  "pdf417",
  "qr_code",
  "upc_a",
  "upc_e",
];

interface BarcodeDetector {
  detect(input: HTMLVideoElement): Promise<Array<Barcode>>;
}

interface BarcodeDetectorConstructor {
  new ({ formats }: { formats: Array<BarcodeFormat> }): BarcodeDetector;
}

declare global {
  interface Window {
    BarcodeDetector: BarcodeDetectorConstructor;
  }
}

type AllBarcodeScannerArgs = {
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  buttonPrefix: string;
};

export default function AllBarcodeScanner({
  onClose,
  onScan,
  buttonPrefix,
}: AllBarcodeScannerArgs): React.ReactNode {
  const { uiStore } = useStores();

  const [loading, setLoading] = useState<boolean>(true);
  const [barcode, setBarcode] = useState<BarcodeInput | null>(null);
  const [error, setError] = useState(false);

  const videoElem = useRef<HTMLVideoElement | null>(null);

  let barcodeDetector: BarcodeDetector;
  let detectionInterval: number;
  let stream: MediaStream;

  /* check for API support in browser, init browser's BarcodeDetector */
  if ("BarcodeDetector" in window) {
    //eslint-disable-next-line no-undef
    barcodeDetector = new window.BarcodeDetector({
      formats: supportedFormats,
    });
  }

  async function startScanner(videoEl: HTMLVideoElement) {
    try {
      /* start video detector / stream */
      stream = await navigator.mediaDevices?.getUserMedia({
        video: {
          facingMode: { ideal: "environment" },
        },
        audio: false,
      });
      if (videoEl) {
        /* assign stream as media source object */
        videoEl.srcObject = stream;
        await videoEl.play();
      }

      /* check (browser) support (also done in parent conditional) */
      if (barcodeDetector) {
        detectionInterval = window.setInterval(
          doNotAwait(async () => {
            if (!videoEl) throw new TypeError("videoEl must not be null");
            const barcodes: Array<Barcode> = await barcodeDetector.detect(
              videoEl
            );
            if (barcodes.length <= 0) return;
            const format: BarcodeFormat = barcodes[0].format;
            const rawValue: string = barcodes[0].rawValue;
            const detectedBarcode = { format, rawValue };
            setBarcode(detectedBarcode);
          }),
          SCAN_INTERVAL
        );
      } else {
        throw new Error("Barcode Detector API not supported");
      }
    } catch (e) {
      if (e instanceof Error)
        uiStore.addAlert(
          mkAlert({
            title: "Unable to start camera with Barcode Detector.",
            message:
              e instanceof DOMException
                ? "Permission to use camera was denied. Please reset camera permission and try again."
                : e.message,
            variant: "error",
            isInfinite: true,
          })
        );
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  function stopScanner() {
    clearInterval(detectionInterval);
    /* stop stream (tracks) */
    stream?.getTracks().forEach((track) => {
      track.stop();
    });
  }

  useEffect(() => {
    const videoEl = videoElem.current;
    if (!videoEl) return;
    void startScanner(videoEl);
    if (videoEl) return () => stopScanner();
  }, [videoElem]);

  return (
    <BarcodeScannerSkeleton
      onClose={onClose}
      onScan={onScan}
      buttonPrefix={buttonPrefix}
      beforeScanHelpText="all formats supported"
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
        ) : null
      }
      error={error}
    />
  );
}
