import Alert from "@mui/material/Alert";
import QrScanner from "qr-scanner";
import type React from "react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { mkAlert } from "../../../stores/contexts/Alert";
import useStores from "../../../stores/use-stores";
import BarcodeScannerSkeleton, { type BarcodeInput } from "./BarcodeScannerSkeleton";

type QrCodeScannerArgs = {
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  buttonPrefix: string;
};

export default function QrCodeScanner({ onClose, onScan, buttonPrefix }: QrCodeScannerArgs): React.ReactNode {
  const { uiStore } = useStores();
  const { t } = useTranslation("inventory");
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
        },
      );
      await qrScanner.start();
    } catch (e) {
      if (typeof e === "string")
        uiStore.addAlert(
          mkAlert({
            title: t("barcodeScanner.startError.title"),
            message: e === "Camera not found." ? t("barcodeScanner.startError.cameraReset") : e,
            variant: "error",
            isInfinite: true,
          }),
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
      beforeScanHelpText={t("barcodeScanner.supportedFormats.qr")}
      videoElem={videoElem}
      barcode={barcode}
      setBarcode={setBarcode}
      loading={loading}
      warning={
        error
          ? !loading && <Alert severity="warning">{t("barcodeScanner.cameraError")}</Alert>
          : !loading && !barcode?.rawValue && <Alert severity="warning">{t("barcodeScanner.otherFormats")}</Alert>
      }
      error={error}
    />
  );
}
