import { alertClasses } from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import { mkAlert } from "@/stores/contexts/Alert";
import { type Barcode, barcodeFormatAsString } from "@/util/barcode";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import useStores from "../../../stores/use-stores";

export type BarcodeInput = Barcode | { rawValue: string; format: "Unknown" };

type BarcodeScannerSkeletonArgs = {
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  beforeScanHelpText: string;
  barcode: BarcodeInput | null;
  loading: boolean;
  warning?: React.ReactNode;
  videoElem: React.RefObject<HTMLVideoElement | null>;
  error: boolean;
};

export default function BarcodeScannerSkeleton({
  onClose,
  onScan,
  beforeScanHelpText,
  barcode,
  loading,
  warning,
  videoElem,
  error,
}: BarcodeScannerSkeletonArgs): React.ReactNode {
  const { uiStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);

  const handleOnSubmit = useCallback(() => {
    try {
      if (!barcode || typeof barcode.rawValue !== "string") {
        uiStore.addAlert(
          mkAlert({
            title: t("barcodeScanner.scanError.title"),
            message: t("barcodeScanner.scanError.message"),
            variant: "error",
            isInfinite: true,
          }),
        );
        return;
      }
      onScan(barcode);
    } catch (e) {
      if (e instanceof Error)
        uiStore.addAlert(
          mkAlert({
            title: t("barcodeScanner.scanError.title"),
            message: e.message,
            variant: "error",
            isInfinite: true,
          }),
        );
    } finally {
      onClose();
    }
  }, [barcode, onScan, onClose, uiStore, t]);

  /*
   * The ref guards against the camera re-detecting the same code on a later
   * interval tick before the dialog has finished closing.
   */
  const submitted = useRef(false);
  useEffect(() => {
    if (barcode?.rawValue && !submitted.current) {
      submitted.current = true;
      handleOnSubmit();
    }
  }, [barcode, handleOnSubmit]);

  const status = loading ? (
    t("barcodeScanner.loading")
  ) : barcode?.rawValue ? (
    <>
      {t("barcodeScanner.barcodeDetected", { format: barcodeFormatAsString(barcode.format) })}
      <br />
      {`${barcode.rawValue}`}
    </>
  ) : (
    beforeScanHelpText
  );

  return (
    <Stack
      sx={{
        p: 1.5,
        width: 440,
        maxWidth: "80vw",
        /*
         * By default an Alert stretches its icon, message, and action
         * slots to full height, leaving single-line text and the icon
         * sitting high; centering the slots keeps them aligned.
         */
        [`& .${alertClasses.root}`]: { alignItems: "center" },
      }}
      spacing={1}
    >
      <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }} spacing={1}>
        {/* keep the help icon out of the DialogTitle: its tooltip would land in the dialog's accessible name */}
        <DialogTitle sx={{ p: 0 }}>{t("barcodeScanner.heading")}</DialogTitle>
        <HelpLinkIcon link={helpDocsArticleUrl("barcodes")} title={t("barcodeScanner.helpTitle")} />
      </Stack>
      <Typography variant="body2" sx={{ color: "text.secondary" }}>
        {status}
      </Typography>
      {/* hide via CSS on detection (not on loading or scanner won't start in Safari)  */}
      <Box
        component="video"
        ref={videoElem}
        sx={{
          display: barcode?.rawValue || error ? "none" : "block",
          width: "100%",
          maxHeight: "45vh",
        }}
      />
      {warning ? <Box sx={{ width: "100%" }}>{warning}</Box> : null}
      <Stack direction="row" sx={{ justifyContent: "flex-end", width: "100%" }}>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
      </Stack>
    </Stack>
  );
}
