import React, { useState, useRef } from "react";
import { observer } from "mobx-react-lite";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextDialog from "../../ContextMenu/ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import useStores from "../../../../stores/use-stores";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import Alert from "@mui/material/Alert";
import { type BarcodeRecord } from "../../../../stores/definitions/Barcode";
import PrintContents, { PreviewPrintItem } from "./PrintContents";
import ReactToPrint from "react-to-print";
import docLinks from "../../../../assets/DocLinks";
import clsx from "clsx";
import { mkAlert } from "../../../../stores/contexts/Alert";
import { useIsSingleColumnLayout } from "../../Layout/Layout2x1";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import Stack from "@mui/material/Stack";

const useStyles = makeStyles()((theme) => ({
  rowWrapper: {
    display: "flex",
    flexDirection: "row",
    width: "100%",
    justifyContent: "space-between",
  },
  columnWrapper: {
    display: "flex",
    flexDirection: "column-reverse",
    alignItems: "center",
    width: "100%",
  },
  fullWidth: { width: "100%" },
  halfWidth: { width: "50%" },
  previewWrapper: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    margin: theme.spacing(0.5),
  },
  centered: {
    textAlign: "center",
    marginBottom: theme.spacing(1),
  },
  topSpaced: { marginTop: theme.spacing(1) },
  hidden: { display: "none" },
}));

export type PrinterType = "GENERIC" | "LABEL";
export type PrintLayout = "BASIC" | "FULL";
export type PrintSize = "SMALL" | "LARGE";

export type PrintOptions = {
  printerType: PrinterType;
  printLayout: PrintLayout;
  printSize: PrintSize;
};

type PrintDialogArgs = {
  showPrintDialog: boolean;
  onClose: () => void;
  imageLinks: Array<string>;
  itemsToPrint: Array<[BarcodeRecord, InventoryRecord]>; // LoM ...
  printerType?: PrinterType;
  printSize?: PrintSize;
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void;
};

type OptionsWrapperArgs = {
  printOptions: PrintOptions;
  setPrintOptions: (options: PrintOptions) => void;
};

export const PrintOptionsWrapper = ({
  printOptions,
  setPrintOptions,
}: OptionsWrapperArgs) => {
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { classes } = useStyles();

  return (
    <FormControl
      component="fieldset"
      className={isSingleColumnLayout ? classes.fullWidth : classes.halfWidth}
    >
      <Stack spacing={3}>
        <FormControl>
          <FormLabel id="printer-type-radiogroup-label">Printer Type</FormLabel>
          <RadioGroup
            aria-labelledby="printer-type-radiogroup-label"
            value={printOptions.printerType}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printerType: target.value as PrinterType,
                });
            }}
            row
          >
            <FormControlLabel
              value="GENERIC"
              control={<Radio size="small" />}
              label="Standard Printer"
            />
            <FormControlLabel
              value="LABEL"
              control={<Radio size="small" />}
              label="Label Printer"
            />
          </RadioGroup>
          {printOptions.printerType === "GENERIC" ? (
            <Alert severity="info">
              Print multiple labels per sheet (e.g. A4 / A3 / Letter).
            </Alert>
          ) : (
            <Alert severity="info">
              Print one label per sticker (Zebra printer).
            </Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-layout-radiogroup-label">Print Layout</FormLabel>
          <RadioGroup
            aria-labelledby="print-layout-radiogroup-label"
            value={printOptions.printLayout}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printLayout: target.value as PrintLayout,
                });
            }}
            row
          >
            <FormControlLabel
              value="FULL"
              control={<Radio size="small" />}
              label="Full"
            />
            <FormControlLabel
              value="BASIC"
              control={<Radio size="small" />}
              label="Basic"
            />
          </RadioGroup>
          {printOptions.printerType === "LABEL" && (
            <Alert severity="info" className={classes.topSpaced}>
              The label shape should match the selected layout. Also, you might
              have problems when using Safari. Please check barcodes{" "}
              <a
                href={docLinks.barcodesPrinting}
                target="_blank"
                rel="noreferrer"
              >
                documentation
              </a>
              .
            </Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-size-radiogroup-label">Print Size</FormLabel>
          {printOptions.printerType === "GENERIC" && (
            <RadioGroup
              aria-labelledby="print-size-radiogroup-label"
              value={printOptions.printSize}
              onChange={({ target }) => {
                if (target.value)
                  setPrintOptions({
                    ...printOptions,
                    printSize: target.value as PrintSize,
                  });
              }}
              row
            >
              <FormControlLabel
                value="LARGE"
                control={<Radio size="small" />}
                label="Large"
              />
              <FormControlLabel
                value="SMALL"
                control={<Radio size="small" />}
                label="Small"
              />
            </RadioGroup>
          )}
          <Alert severity="info">
            {printOptions.printerType === "LABEL"
              ? "For label printers size is set automatically (to match a range of label sizes)."
              : printOptions.printSize === "LARGE"
              ? "Full width (4cm)."
              : "Half width (2cm)."}
          </Alert>
        </FormControl>
      </Stack>
    </FormControl>
  );
};

function PrintDialog({
  showPrintDialog,
  onClose,
  imageLinks,
  itemsToPrint,
  printerType,
  printSize,
  closeMenu,
}: PrintDialogArgs) {
  const { classes } = useStyles();
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<HTMLDivElement>(null);

  const [item, itemOwner] = itemsToPrint[0];

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
    printerType: printerType ?? "GENERIC",
    printLayout: "FULL",
    printSize: printSize ?? "LARGE",
  });

  const handleClose = () => {
    onClose();
    if (typeof closeMenu === "function") closeMenu();
  };

  const HelperText = () => (
    <>
      <Typography variant="body2" className={classes.centered}>
        <strong>Preview Barcode Label Layout</strong>
      </Typography>
    </>
  );

  return (
    <ContextDialog
      open={showPrintDialog}
      onClose={handleClose}
      fullWidth
      maxWidth="lg"
    >
      <DialogTitle>Print Options</DialogTitle>
      <DialogContent>
        <Box
          className={
            isSingleColumnLayout ? classes.columnWrapper : classes.rowWrapper
          }
        >
          <PrintOptionsWrapper
            printOptions={printOptions}
            setPrintOptions={setPrintOptions}
          />
          <div
            className={clsx(
              classes.previewWrapper,
              isSingleColumnLayout ? classes.fullWidth : classes.halfWidth
            )}
          >
            <HelperText />
            {/* we preview only one item, resulting from choice of print options */}
            <PreviewPrintItem
              index={0}
              printOptions={printOptions}
              item={item}
              itemOwner={itemOwner}
              imageLinks={imageLinks}
              target="screen"
            />
          </div>
          {/* we need the whole component for ReactToPrint, but not visible here */}
          <div className={classes.hidden}>
            <PrintContents
              ref={componentToPrint}
              printOptions={printOptions}
              itemsToPrint={itemsToPrint}
              imageLinks={imageLinks}
              target={
                printOptions.printerType === "GENERIC"
                  ? "multiplePrint"
                  : "singlePrint"
              }
            />
          </div>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          Cancel
        </Button>
        <ReactToPrint
          trigger={() => (
            <Button
              // do not add an onClick, it would be overwritten
              color="primary"
              variant="contained"
              disableElevation
              disabled={false}
            >
              {`Print selected (${itemsToPrint.length})`}
            </Button>
          )}
          content={() => componentToPrint.current}
          onAfterPrint={() => {
            handleClose();
          }}
          onPrintError={(errorLocation, error) => {
            uiStore.addAlert(
              mkAlert({
                title: "Print error.",
                message: error.message || "",
                variant: "error",
                isInfinite: true,
              })
            );
          }}
        />
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(PrintDialog);
