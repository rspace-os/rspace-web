import React from "react";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import CardActions from "@mui/material/CardActions";
import useStores from "../../../stores/use-stores";
import { mkAlert } from "@/stores/contexts/Alert";
import HelpTextAlert from "../../../components/HelpTextAlert";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Divider from "@mui/material/Divider";
import Box from "@mui/material/Box";
import StringField from "../../../components/Inputs/StringField";
import { barcodeFormatAsString, type Barcode } from "@/util/barcode";
import FormField from "../../../components/Inputs/FormField";

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
  videoElem: React.RefObject<HTMLVideoElement>;
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

  function handleOnSubmit() {
    try {
      if (!barcode || typeof barcode.rawValue !== "string") {
        uiStore.addAlert(
          mkAlert({
            title: "An error occurred.",
            message: "Unable to search. Scan has not completed.",
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
            title: "An error occurred.",
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
        <HelpTextAlert
          severity="info"
          condition={true}
          text={
            loading ? (
              `Loading Barcode Scanner...`
            ) : barcode?.rawValue ? (
              <>
                {`Barcode detected: ${barcodeFormatAsString(barcode.format)}
                format.`}
                <br />
                {`${barcode.rawValue}`}
              </>
            ) : (
              `Barcode Scanner: ${beforeScanHelpText}.`
            )
          }
        />
        <HelpLinkIcon
          link={docLinks.barcodes}
          title="Info on using barcodes."
        />
      </Stack>
      <CardActions>
        <Button
          onClick={() => {
            onClose();
          }}
        >
          Cancel
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
            Clear
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
                label="Alternatively, enter the data encoded in the barcode"
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
