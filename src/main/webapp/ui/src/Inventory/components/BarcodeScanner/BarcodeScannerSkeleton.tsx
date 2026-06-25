import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CardActions from "@mui/material/CardActions";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import type React from "react";
import { useTranslation } from "react-i18next";
import { mkAlert } from "@/stores/contexts/Alert";
import { type Barcode, barcodeFormatAsString } from "@/util/barcode";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import FormField from "../../../components/Inputs/FormField";
import StringField from "../../../components/Inputs/StringField";
import useStores from "../../../stores/use-stores";

export type BarcodeInput = Barcode | { rawValue: string; format: "Unknown" };

type BarcodeScannerSkeletonArgs = {
  onClose: () => void;
  onScan: (scannedBarcodeInput: BarcodeInput) => void;
  buttonPrefix: string;
  beforeScanHelpText: string;
  barcode: BarcodeInput | null;
  setBarcode: (value: BarcodeInput | null) => void;
  loading: boolean;
  warning?: React.ReactNode;
  videoElem: React.RefObject<HTMLVideoElement | null>;
  error: boolean;
};

export default function BarcodeScannerSkeleton({
  onClose,
  onScan,
  buttonPrefix,
  beforeScanHelpText,
  barcode,
  setBarcode,
  loading,
  warning,
  videoElem,
  error,
}: BarcodeScannerSkeletonArgs): React.ReactNode {
  const { uiStore } = useStores();
  const { t } = useTranslation("inventory");

  function handleOnSubmit() {
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
  }

  return (
    <Stack sx={{ alignItems: "center" }}>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-around",
          width: "100%",
          marginBottom: "8px",
        }}
      >
        <Alert severity="info">
          {loading ? (
            t("barcodeScanner.loading")
          ) : barcode?.rawValue ? (
            <>
              {t("barcodeScanner.barcodeDetected", { format: barcodeFormatAsString(barcode.format) })}
              <br />
              {`${barcode.rawValue}`}
            </>
          ) : (
            t("barcodeScanner.prompt", { helpText: beforeScanHelpText })
          )}
        </Alert>
        <HelpLinkIcon link={docLinks.barcodes} title={t("barcodeScanner.helpTitle")} />
      </Stack>
      <CardActions>
        <Button
          onClick={() => {
            onClose();
          }}
        >
          {t("actions.cancel", { ns: "common" })}
        </Button>
        <Button
          disabled={!barcode?.rawValue}
          color="callToAction"
          variant="contained"
          disableElevation
          onClick={handleOnSubmit}
        >
          {buttonPrefix}
        </Button>
        {barcode && (
          <Button
            onClick={() => {
              setBarcode(null);
            }}
          >
            {t("actions.clear", { ns: "common" })}
          </Button>
        )}
      </CardActions>
      {/* hide via CSS on detection (not on loading or scanner won't start in Safari)  */}
      <Box
        component="video"
        ref={videoElem}
        sx={(theme) => ({
          display: barcode?.rawValue || error ? "none" : "block",
          height: "37vh",
          width: "37vw",
          [theme.breakpoints.down("sm")]: {
            width: "100%",
            height: "100%",
            maxHeight: "50vh",
            maxWidth: "80vw",
          },
        })}
      />
      {warning !== null && <Box>{warning}</Box>}
      {(!barcode || barcode.format === "Unknown") && (
        <>
          <Box sx={{ width: "100%" }}>
            <Box sx={{ m: 1 }}>
              <Divider orientation="horizontal" />
            </Box>
          </Box>
          <Box sx={{ alignSelf: "flex-start" }}>
            <Box sx={{ m: 1 }}>
              <FormField
                label={t("barcodeScanner.altEntry")}
                renderInput={(props) => (
                  <StringField
                    {...props}
                    variant="standard"
                    onChange={({ target: { value } }) => {
                      setBarcode({
                        rawValue: value,
                        format: "Unknown",
                      });
                    }}
                  />
                )}
                value={barcode?.rawValue ?? ""}
              />
            </Box>
          </Box>
        </>
      )}
    </Stack>
  );
}
