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
import * as ArrayUtils from "../../../util/ArrayUtils";
import ApiService from "../../../common/InvApiService";

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
  /*
   * All records have a global ID, but some also have an IGSN. The print dialog
   * should support printing either, where possible.
   */
  printIdentifierType?: "GLOBAL ID" | "IGSN",

  printerType: PrinterType,
  printLayout: PrintLayout,
  printSize: PrintSize,

  /*
   * Each label can be printed multiple times (e.g. for raffle books).
   * - If the number is 1 then the labels are printed in a grid layout. and if
   * - If the number is 2 then each label is printed on its own row, wrapping
   *   where necessary.
   * - Any other numberical value is invalid.
   */
  printCopies?: "1" | "2",
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
  printType: PrintType,
  itemsToPrint: $ReadOnlyArray<InventoryRecord>,
  printerType?: PrinterType,
  printSize?: PrintSize,
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void,
|};

type OptionsWrapperArgs = {|
  itemsToPrint: $ReadOnlyArray<[BarcodeRecord, InventoryRecord]>,
  printOptions: PrintOptions,
  setPrintOptions: (PrintOptions) => void,
  printType: PrintType,
|};

export const PrintOptionsWrapper = ({
  itemsToPrint,
  printOptions,
  setPrintOptions,
}: OptionsWrapperArgs): Node => {
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { classes } = useStyles();

  const printIdentifierTypeOptions: Array<RadioOption<"GLOBAL ID" | "IGSN">> = [
    { value: "GLOBAL ID", label: "Global ID" },
    { value: "IGSN", label: "IGSN" },
  ];

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

  /*
   * Whilst the rest of the code handles any arbitrary number of copies, the
   * UI only allows for 1 or 2 copies. This is because the only use case for
   * multiple copies is for raffle books, where each identifier is printed
   * twice. This is a common use case, and so is the only one that is
   * implemented for now.
   */
  const printCopiesOptions: Array<RadioOption<"1" | "2">> = [
    { value: "1", label: "Each identifier once" },
    { value: "2", label: "Each identifier twice (raffle book)" },
  ];

  return (
    <FormControl
      component="fieldset"
      className={isSingleColumnLayout ? classes.fullWidth : classes.halfWidth}
    >
      <Box className={classes.optionModuleWrapper}>
        <FormLabel id="identifiers-type-radiogroup-label">
          Identifier Type
        </FormLabel>
        <RadioField
          name={"Identifier Type Options"}
          value={printOptions.printIdentifierType ?? "GLOBAL ID"}
          onChange={({ target }) => {
            if (target.value)
              setPrintOptions({
                ...printOptions,
                printIdentifierType: target.value,
              });
          }}
          options={printIdentifierTypeOptions}
          disabled={false}
          labelPlacement="bottom"
          row
          smallText={true}
        />
        {printOptions.printIdentifierType === "IGSN" &&
          itemsToPrint.some(
            ([_, record]) => record.identifiers.length === 0
          ) && (
            <Alert severity="error">
              Some of the selected records do not have an IGSN.
            </Alert>
          )}
      </Box>
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
      <Box className={classes.optionModuleWrapper}>
        <FormLabel id="print-copties-radiogroup-label">Print Copies</FormLabel>
        {printOptions.printerType === "GENERIC" && (
          <RadioField
            name={"Print Copies Options"}
            value={printOptions.printCopies ?? "1"}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printCopies: target.value,
                });
            }}
            options={printCopiesOptions}
            disabled={false}
            labelPlacement="bottom"
            row
            smallText={true}
          />
        )}
        {printOptions.printerType === "LABEL" && (
          <Alert severity="info">
            For label printers, the number of copies is set to 1 per item.
          </Alert>
        )}
      </Box>
    </FormControl>
  );
};

function PrintDialog({
  showPrintDialog,
  onClose,
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

  const itemsAndTheirBarcodes: $ReadOnlyArray<[ BarcodeRecord, InventoryRecord ]> = itemsToPrint.map((record) => [
    record.barcodes[0],
    record,
  ]);

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
    printIdentifierType: "GLOBAL ID",
    printerType: printerType ?? "GENERIC",
    printLayout: "FULL",
    printSize: printSize ?? "LARGE",
    printCopies: "1",
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

  const [imageLinks, setImageLinks] = React.useState<$ReadOnlyArray<string>>([]);
  React.useEffect(() => {
    if (printOptions.printIdentifierType === "IGSN" && itemsToPrint.some(
      (record) => record.identifiers.length === 0
    )) {
      return;
    }
    if (printOptions.printIdentifierType === "GLOBAL ID" && itemsToPrint.some(
      (record) => record.barcodes.length === 0
    )) {
      return;
    }

    imageLinks.forEach((img) => URL.revokeObjectURL(img));

    const getImageUrl = async (record: InventoryRecord) => {
      if (printOptions.printIdentifierType === "IGSN") {
        const { data } = await ApiService.query<{||}, Blob>("/barcodes", new URLSearchParams({
          content: `https://doi.org/${record.identifiers[0].doi}`,
          barcodeType: "QR",
        }), true);
        const file = new File([data], "", { type: "image/png" });
        return URL.createObjectURL(file);
      }
      return URL.createObjectURL(await record.barcodes[0].fetchImage());
    }

    Promise.all(itemsToPrint.map(getImageUrl)).then((imageUrls) => {
      setImageLinks(imageUrls);
    }).catch((e) => {
      uiStore.addAlert(
        mkAlert({
          title: "Unable to retrieve barcode images.",
          message: e.message || "",
          variant: "error",
          isInfinite: true,
        })
      );
    });
  }, [itemsToPrint, printOptions]);

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
            itemsToPrint={itemsAndTheirBarcodes}
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
            {printOptions.printIdentifierType === "IGSN" &&
            itemsToPrint.some((record) => record.identifiers.length === 0)
              ? "Please resolve error."
              : ArrayUtils.head(itemsAndTheirBarcodes)
                  .map(([barcode, inventoryRecord]) => (
                    <PreviewPrintItem
                      index={0}
                      printOptions={printOptions}
                      printType={printType}
                      barcode={barcode}
                      itemOwner={inventoryRecord}
                      imageLinks={imageLinks}
                      target="screen"
                    />
                  ))
                  .elseThrow()}
          </div>
          {/* we need the whole component for ReactToPrint, but not visible here */}
          <div className={classes.hidden}>
            <PrintContents
              ref={componentToPrint}
              printOptions={printOptions}
              printType={printType}
              itemsToPrint={itemsAndTheirBarcodes}
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
              disabled={
                printOptions.printIdentifierType === "IGSN" &&
                itemsToPrint.some(
                  (record) => record.identifiers.length === 0
                )
              }
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
