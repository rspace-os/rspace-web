//@flow

import React, { useState, useRef, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextDialog from "../ContextMenu/ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import useStores from "../../../stores/use-stores";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Alert from "@mui/material/Alert";
import { type BarcodeRecord } from "../../../stores/definitions/Barcode";
import PrintContents, { PreviewPrintItem } from "./PrintContents";
import ReactToPrint from "react-to-print";
import docLinks from "../../../assets/DocLinks";
import clsx from "clsx";
import { mkAlert } from "../../../stores/contexts/Alert";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

const useStyles = makeStyles()((theme) => ({
  optionModuleWrapper: {
    border: `1px solid ${theme.palette.sidebar.selected.bg}`,
    borderRadius: theme.spacing(0.5),
    padding: theme.spacing(1),
    marginBottom: theme.spacing(2),
  },
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
  printerType: PrinterType,
  printLayout: PrintLayout,
  printSize: PrintSize,
};

/**
 * PrintDialog is intended to be used - in the future - for more than barcodes
 * itemsToPrint are connected to printType
 * last 2 print types are probably going to be implemented later
 */

export type PrintType =
  | "barcodeLabel"
  | "contextMenu"
  | "recordDetails"
  | "listOfMaterials";

type PrintDialogArgs = {|
  showPrintDialog: boolean,
  onClose: () => void,
  imageLinks: Array<string>,
  printType: PrintType,
  itemsToPrint: Array<[BarcodeRecord, InventoryRecord]>, // LoM ...
  printerType?: PrinterType,
  printSize?: PrintSize,
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void,
|};

type OptionsWrapperArgs = {|
  printOptions: PrintOptions,
  setPrintOptions: (PrintOptions) => void,
  printType: PrintType,
|};

export const PrintOptionsWrapper = ({
  printOptions,
  setPrintOptions,
}: OptionsWrapperArgs): Node => {
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { classes } = useStyles();

  const printerTypeOptions: Array<RadioOption<PrinterType>> = [
    { value: "GENERIC", label: "Standard Printer" },
    { value: "LABEL", label: "Label Printer" },
  ];

  const printLayoutOptions: Array<RadioOption<PrintLayout>> = [
    { value: "FULL", label: "Full" },
    { value: "BASIC", label: "Basic" },
  ];

  const printSizeOptions: Array<RadioOption<PrintSize>> = [
    { value: "LARGE", label: "Large" },
    { value: "SMALL", label: "Small" },
  ];

  return (
    <FormControl
      component="fieldset"
      className={isSingleColumnLayout ? classes.fullWidth : classes.halfWidth}
    >
      <Box className={classes.optionModuleWrapper}>
        <FormLabel id="printer-type-radiogroup-label">Printer Type</FormLabel>
        <RadioField
          name={"Printer Type Options"}
          value={printOptions.printerType}
          onChange={({ target }) => {
            if (target.value)
              setPrintOptions({
                ...printOptions,
                printerType: target.value,
              });
          }}
          options={printerTypeOptions}
          disabled={false}
          labelPlacement="bottom"
          row
          smallText={true}
        />
        {printOptions.printerType === "GENERIC" ? (
          <Alert severity="info">
            Print multiple labels per sheet (e.g. A4 / A3 / Letter).
          </Alert>
        ) : (
          <Alert severity="info">
            Print one label per sticker (Zebra printer).
          </Alert>
        )}
      </Box>
      <Box className={classes.optionModuleWrapper}>
        <FormLabel id="print-layout-radiogroup-label">Print Layout</FormLabel>
        <RadioField
          name={"Print Layout Options"}
          value={printOptions.printLayout}
          onChange={({ target }) => {
            if (target.value)
              setPrintOptions({
                ...printOptions,
                printLayout: target.value,
              });
          }}
          options={printLayoutOptions}
          disabled={false}
          labelPlacement="bottom"
          row
          smallText={true}
        />
        <Alert severity="info">
          {printOptions.printLayout === "FULL"
            ? `Barcode and record details${
                printOptions.printerType === "LABEL"
                  ? " (rectangular label required)."
                  : "."
              }`
            : `Barcode and global ID${
                printOptions.printerType === "LABEL"
                  ? " (square or rectangular label)."
                  : "."
              }`}
        </Alert>
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
      </Box>
      <Box className={classes.optionModuleWrapper}>
        <FormLabel id="print-size-radiogroup-label">Print Size</FormLabel>
        {printOptions.printerType === "GENERIC" && (
          <RadioField
            name={"Print Size Options"}
            value={printOptions.printSize}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printSize: target.value,
                });
            }}
            options={printSizeOptions}
            disabled={false}
            labelPlacement="bottom"
            row
            smallText={true}
          />
        )}
        <Alert severity="info">
          {printOptions.printerType === "LABEL"
            ? "For label printers size is set automatically (to match a range of label sizes)."
            : printOptions.printSize === "LARGE"
            ? "Full width (4cm)."
            : "Half width (2cm)."}
        </Alert>
      </Box>
    </FormControl>
  );
};

function PrintDialog({
  showPrintDialog,
  onClose,
  imageLinks,
  printType,
  itemsToPrint,
  printerType,
  printSize,
  closeMenu,
}: PrintDialogArgs): Node {
  const { classes } = useStyles();
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<mixed>();
  const barcodePrint = ["contextMenu", "barcodeLabel"].includes(printType);

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
        <strong>
          Preview
          {barcodePrint ? `: Barcode Label Layout` : ""}
        </strong>
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
            printType={printType}
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
              printType={printType}
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
              printType={printType}
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
          onPrintError={(e) => {
            uiStore.addAlert(
              mkAlert({
                title: "Print error.",
                message: e.message || "",
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

export default (observer(PrintDialog): ComponentType<PrintDialogArgs>);
