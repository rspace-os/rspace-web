//@flow

import React, { useState, useRef, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextDialog from "../../components/ContextMenu/ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import useStores from "../../../stores/use-stores";
import Alert from "@mui/material/Alert";
import PrintContents, {
  PreviewPrintItem,
} from "../../components/Print/PrintContents";
import ReactToPrint from "react-to-print";
import docLinks from "../../../assets/DocLinks";
import clsx from "clsx";
import { mkAlert } from "../../../stores/contexts/Alert";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import * as ArrayUtils from "../../../util/ArrayUtils";
import ApiService from "../../../common/InvApiService";
import Stack from "@mui/material/Stack";
import { Optional } from "../../../util/optional";
import { type Identifier } from "../../useIdentifiers";

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

type PrintDialogArgs = {|
  showPrintDialog: boolean,
  onClose: () => void,
  itemsToPrint: $ReadOnlyArray<Identifier>,
  printerType?: PrinterType,
  printSize?: PrintSize,
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void,
|};

type OptionsWrapperArgs = {|
  printOptions: PrintOptions,
  setPrintOptions: (PrintOptions) => void,
|};

export const PrintOptionsWrapper = ({
  printOptions,
  setPrintOptions,
}: OptionsWrapperArgs): Node => {
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
                  printerType: target.value,
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
                  printLayout: target.value,
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
          <FormLabel id="print-copties-radiogroup-label">
            Print Copies
          </FormLabel>
          {printOptions.printerType === "GENERIC" && (
            <RadioGroup
              aria-labelledby="print-copties-radiogroup-label"
              value={printOptions.printCopies}
              onChange={({ target }) => {
                if (target.value)
                  setPrintOptions({
                    ...printOptions,
                    printCopies: target.value,
                  });
              }}
            >
              <FormControlLabel
                value="1"
                control={<Radio size="small" />}
                label="Each barcode once"
              />
              <FormControlLabel
                value="2"
                control={<Radio size="small" />}
                label="Each barcode twice (raffle book)"
              />
            </RadioGroup>
          )}
          {printOptions.printerType === "LABEL" && (
            <Alert severity="info">
              For label printers, the number of copies is set to 1 per item.
            </Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-size-radiogroup-label">Print Size</FormLabel>
          {printOptions.printerType === "GENERIC" &&
            printOptions.printCopies === "1" && (
              <RadioGroup
                aria-labelledby="print-size-radiogroup-label"
                value={printOptions.printSize}
                onChange={({ target }) => {
                  if (target.value)
                    setPrintOptions({
                      ...printOptions,
                      printSize: target.value,
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
              : printOptions.printCopies === "2"
              ? "Applying a horizontal layout."
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
  itemsToPrint,
  printerType,
  printSize,
  closeMenu,
}: PrintDialogArgs): Node {
  const { classes } = useStyles();
  const { uiStore, trackingStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<mixed>();

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
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
        <strong>Preview Barcode Label Layout</strong>
      </Typography>
    </>
  );

  const [imageLinks, setImageLinks] = React.useState<$ReadOnlyArray<string>>(
    []
  );
  React.useEffect(() => {
    imageLinks.forEach((img) => URL.revokeObjectURL(img));

    const getImageUrl = async (identifier: Identifier) => {
      const { data } = await ApiService.query<{||}, Blob>(
        "/barcodes",
        new URLSearchParams({
          content: `https://doi.org/${identifier.doi}`,
          barcodeType: "QR",
        }),
        true
      );
      const file = new File([data], "", { type: "image/png" });
      return URL.createObjectURL(file);
    };

    Promise.all(itemsToPrint.map(getImageUrl))
      .then((imageUrls) => {
        setImageLinks(imageUrls);
      })
      .catch((e) => {
        uiStore.addAlert(
          mkAlert({
            title: "Unable to retrieve barcode images.",
            message: e.message || "",
            variant: "error",
            isInfinite: true,
          })
        );
      });
  }, [itemsToPrint]);

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
            {imageLinks.length === itemsToPrint.length ? (
              <>
                {/* we preview only one item, resulting from choice of print options */}
                {ArrayUtils.head(itemsToPrint)
                  .map((identifier) => (
                    <PreviewPrintItem
                      key={identifier.doi}
                      index={0}
                      printOptions={{
                        ...printOptions,
                        printIdentifierType: "IGSN",
                      }}
                      printLabelContents={{
                        itemLabel: "-",
                        locationLabel: "-",
                        identifier: Optional.present(identifier),
                        globalId: Optional.empty(),
                        barcodeUrl: imageLinks[0],
                      }}
                      imageLinks={imageLinks}
                      target="screen"
                    />
                  ))
                  .elseThrow()}
                {/* we need the whole component for ReactToPrint, but not visible here */}
                <div className={classes.hidden}>
                  <PrintContents
                    ref={componentToPrint}
                    printOptions={{
                      ...printOptions,
                      printIdentifierType: "IGSN",
                    }}
                    itemsToPrint={ArrayUtils.zipWith(
                      itemsToPrint,
                      imageLinks,
                      (identifier, barcodeUrl) => ({
                        itemLabel: "-",
                        locationLabel: "-",
                        identifier: Optional.present(identifier),
                        globalId: Optional.empty(),
                        barcodeUrl,
                      })
                    )}
                    imageLinks={imageLinks}
                    target={
                      printOptions.printerType === "GENERIC"
                        ? "multiplePrint"
                        : "singlePrint"
                    }
                  />
                </div>
              </>
            ) : (
              <Typography variant="body2">Loading...</Typography>
            )}
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
            >
              {`Print selected (${itemsToPrint.length})`}
            </Button>
          )}
          content={() => componentToPrint.current}
          onAfterPrint={() => {
            handleClose();
            trackingStore.trackEvent("user:print:barcodeLabel", {
              count: itemsToPrint.length,
              printIdentifierType: "IGSN",
              printerType: printOptions.printerType,
              printLayout: printOptions.printLayout,
              printSize: printOptions.printSize,
              printCopies: printOptions.printCopies,
            });
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
