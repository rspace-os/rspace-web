import React from "react";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import CardActions from "@mui/material/CardActions";
import { makeStyles } from "tss-react/mui";
import useStores from "../../../stores/use-stores";
import { mkAlert } from "../../../stores/contexts/Alert";
import HelpTextAlert from "../../../components/HelpTextAlert";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Divider from "@mui/material/Divider";
import Box from "@mui/material/Box";
import StringField from "../../../components/Inputs/StringField";
import {
  barcodeFormatAsString,
  type BarcodeFormat,
} from "../../../util/barcode";
import InputWrapper from "../../../components/Inputs/InputWrapper";

export type Barcode = {
  format: BarcodeFormat;
  rawValue: string;
};

export type BarcodeInput = Barcode | { rawValue: string; format: "Unknown" };

const useStyles = makeStyles()((theme) => ({
  video: {
    height: "37vh",
    width: "37vw",
    [theme.breakpoints.down("sm")]: {
      width: "100%",
      height: "100%",
      maxHeight: "50vh",
      maxWidth: "80vw",
    },
  },
  hidden: { display: "none" },
}));

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
  const { classes } = useStyles();

  function handleOnSubmit() {
    try {
      if (!barcode || typeof barcode.rawValue !== "string")
        throw new Error("Unable to search. Scan has not completed.");
      onScan(barcode);
    } catch (e) {
      if (e instanceof Error)
        uiStore.addAlert(
          mkAlert({
            title: "An error occurred.",
            message: e.message,
            variant: "error",
            isInfinite: true,
          })
        );
    } finally {
      onClose();
    }
  }

  return (
    <Grid container direction="column" alignItems="center">
      <Grid
        container
        direction="row"
        justifyContent="space-around"
        style={{ width: "100%", marginBottom: "8px" }}
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
      </Grid>
      <Grid item>
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
            color="primary"
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
      </Grid>
      {/* hide via CSS on detection (not on loading or scanner won't start in Safari)  */}
      <Grid item>
        <video
          ref={videoElem}
          className={
            barcode?.rawValue || error ? classes.hidden : classes.video
          }
        />
      </Grid>
      {warning !== null && <Grid item>{warning}</Grid>}
      {(!barcode || barcode.format === "Unknown") && (
        <>
          <Grid item style={{ width: "100%" }}>
            <Box m={1}>
              <Divider orientation="horizontal" />
            </Box>
          </Grid>
          <Grid item style={{ alignSelf: "flex-start" }}>
            <Box m={1}>
              <InputWrapper label="Alternatively, enter the data encoded in the barcode">
                <StringField
                  value={barcode?.rawValue ?? ""}
                  variant="standard"
                  onChange={({ target: { value } }) => {
                    setBarcode({
                      rawValue: value,
                      format: "Unknown",
                    });
                  }}
                />
              </InputWrapper>
            </Box>
          </Grid>
        </>
      )}
    </Grid>
  );
}
