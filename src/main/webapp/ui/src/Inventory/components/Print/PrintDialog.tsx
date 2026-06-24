import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useReactToPrint } from "react-to-print";
import { mkAlert } from "@/stores/contexts/Alert";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { Optional } from "@/util/optional";
import { toTitleCase } from "@/util/Util";
import docLinks from "../../../assets/DocLinks";
import ApiService from "../../../common/InvApiService";
import ContainerModel from "../../../stores/models/ContainerModel";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import useStores from "../../../stores/use-stores";
import ContextDialog from "../ContextMenu/ContextDialog";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import PrintContents, { PreviewPrintItem } from "./PrintContents";

export type PrinterType = "GENERIC" | "LABEL";
export type PrintLayout = "BASIC" | "FULL";
export type PrintSize = "SMALL" | "LARGE";

export type PrintOptions = {
  /*
   * All records have a global ID, but some also have an IGSN. The print dialog
   * should support printing either, where possible.
   */
  printIdentifierType?: "GLOBAL ID" | "IGSN";

  printerType: PrinterType;
  printLayout: PrintLayout;
  printSize: PrintSize;

  /*
   * Each label can be printed multiple times (e.g. for raffle books).
   * - If the number is 1 then the labels are printed in a grid layout. and if
   * - If the number is 2 then each label is printed on its own row, wrapping
   *   where necessary.
   * - Any other numberical value is invalid.
   */
  printCopies?: "1" | "2";
};

type PrintDialogArgs = {
  showPrintDialog: boolean;
  onClose: () => void;
  itemsToPrint: ReadonlyArray<InventoryRecord>;
  printerType?: PrinterType;
  printSize?: PrintSize;
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void;
};

type OptionsWrapperArgs = {
  itemsToPrint: ReadonlyArray<InventoryRecord>;
  printOptions: PrintOptions;
  setPrintOptions: (options: PrintOptions) => void;
};

export const PrintOptionsWrapper = ({ itemsToPrint, printOptions, setPrintOptions }: OptionsWrapperArgs): ReactNode => {
  const { t } = useTranslation("inventory");
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <FormControl component="fieldset" sx={{ width: isSingleColumnLayout ? "100%" : "50%" }}>
      <Stack spacing={3}>
        <FormControl>
          <FormLabel id="identifiers-type-radiogroup-label">{t("print.options.identifierType")}</FormLabel>
          <RadioGroup
            aria-labelledby="identifiers-type-radiogroup-label"
            value={printOptions.printIdentifierType}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printIdentifierType: target.value as "GLOBAL ID" | "IGSN",
                });
            }}
            row
          >
            <FormControlLabel value="GLOBAL ID" control={<Radio size="small" />} label={t("print.options.globalId")} />
            <FormControlLabel value="IGSN" control={<Radio size="small" />} label={t("print.options.igsnId")} />
          </RadioGroup>
          {printOptions.printIdentifierType === "IGSN" &&
            itemsToPrint.some((record) => record.identifiers.length === 0) && (
              <Alert severity="error">{t("print.options.igsnMissing")}</Alert>
            )}
        </FormControl>
        <FormControl>
          <FormLabel id="printer-type-radiogroup-label">{t("print.options.printerType")}</FormLabel>
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
              label={t("print.options.standardPrinter")}
            />
            <FormControlLabel value="LABEL" control={<Radio size="small" />} label={t("print.options.labelPrinter")} />
          </RadioGroup>
          {printOptions.printerType === "GENERIC" ? (
            <Alert severity="info">{t("print.options.standardPrinterHint")}</Alert>
          ) : (
            <Alert severity="info">{t("print.options.labelPrinterHint")}</Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-layout-radiogroup-label">{t("print.options.printLayout")}</FormLabel>
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
            <FormControlLabel value="FULL" control={<Radio size="small" />} label={t("print.options.full")} />
            <FormControlLabel value="BASIC" control={<Radio size="small" />} label={t("print.options.basic")} />
          </RadioGroup>
          {printOptions.printerType === "LABEL" && (
            <Alert severity="info" sx={{ mt: 1 }}>
              The label shape should match the selected layout. Also, you might have problems when using Safari. Please
              check barcodes{" "}
              <a href={docLinks.barcodesPrinting} target="_blank" rel="noreferrer">
                documentation
              </a>
              .
            </Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-copties-radiogroup-label">{t("print.options.printCopies")}</FormLabel>
          <RadioGroup
            aria-labelledby="print-copties-radiogroup-label"
            value={printOptions.printCopies}
            onChange={({ target }) => {
              if (target.value)
                setPrintOptions({
                  ...printOptions,
                  printCopies: target.value as "1" | "2",
                });
            }}
          >
            <FormControlLabel value="1" control={<Radio size="small" />} label={t("print.options.eachBarcodeOnce")} />
            <FormControlLabel value="2" control={<Radio size="small" />} label={t("print.options.eachBarcodeTwice")} />
          </RadioGroup>
        </FormControl>
        <FormControl>
          <FormLabel id="print-size-radiogroup-label">{t("print.options.printSize")}</FormLabel>
          {printOptions.printerType === "GENERIC" && printOptions.printCopies === "1" && (
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
              <FormControlLabel value="LARGE" control={<Radio size="small" />} label={t("print.options.large")} />
              <FormControlLabel value="SMALL" control={<Radio size="small" />} label={t("print.options.small")} />
            </RadioGroup>
          )}
          <Alert severity="info">
            {printOptions.printerType === "LABEL"
              ? t("print.options.labelPrinterAutoSize")
              : printOptions.printCopies === "2"
                ? t("print.options.horizontalLayout")
                : printOptions.printSize === "LARGE"
                  ? t("print.options.fullWidth")
                  : t("print.options.halfWidth")}
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
}: PrintDialogArgs): ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { uiStore, trackingStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<HTMLDivElement>(null);

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
    printIdentifierType: "GLOBAL ID",
    printerType: printerType ?? "GENERIC",
    printLayout: "FULL",
    printSize: printSize ?? "LARGE",
    printCopies: "1",
  });

  const handleClose = useCallback(() => {
    onClose();
    if (typeof closeMenu === "function") closeMenu();
  }, [onClose, closeMenu]);

  const handlePrint = useReactToPrint({
    contentRef: componentToPrint,
    onAfterPrint: () => {
      handleClose();
      trackingStore.trackEvent("user:print:barcodeLabel", {
        count: itemsToPrint.length,
        printIdentifierType: printOptions.printIdentifierType,
        printerType: printOptions.printerType,
        printLayout: printOptions.printLayout,
        printSize: printOptions.printSize,
        printCopies: printOptions.printCopies,
      });
    },
    onPrintError: (_errorLocation, error) => {
      uiStore.addAlert(
        mkAlert({
          title: t("print.dialog.printError"),
          message: typeof error === "string" ? error : error.message || "",
          variant: "error",
          isInfinite: true,
        }),
      );
    },
  });

  const HelperText = () => (
    <>
      <Typography variant="body2" sx={{ textAlign: "center", mb: 1 }}>
        <strong>{t("print.dialog.previewLabelLayout")}</strong>
      </Typography>
    </>
  );

  const [imageLinks, setImageLinks] = React.useState<ReadonlyArray<string>>([]);
  React.useEffect(() => {
    if (printOptions.printIdentifierType === "IGSN" && itemsToPrint.some((record) => record.identifiers.length === 0)) {
      return;
    }
    if (
      printOptions.printIdentifierType === "GLOBAL ID" &&
      itemsToPrint.some((record) => record.barcodes.length === 0)
    ) {
      return;
    }

    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    imageLinks.forEach((img) => URL.revokeObjectURL(img));

    const getImageUrl = async (record: InventoryRecord) => {
      if (printOptions.printIdentifierType === "IGSN") {
        const { data } = await ApiService.query<Blob>(
          "/barcodes",
          new URLSearchParams({
            content: `https://doi.org/${record.identifiers[0].doi}`,
            barcodeType: "QR",
          }),
          true,
        );
        const file = new File([data], "", { type: "image/png" });
        return URL.createObjectURL(file);
      }
      return URL.createObjectURL(await record.barcodes[0].fetchImage());
    };

    Promise.all(itemsToPrint.map(getImageUrl))
      .then((imageUrls) => {
        setImageLinks(imageUrls);
      })
      .catch((e) => {
        uiStore.addAlert(
          mkAlert({
            title: t("print.dialog.unableToRetrieveBarcodeImages"),
            message: e instanceof Error ? e.message : "",
            variant: "error",
            isInfinite: true,
          }),
        );
      });
  }, [itemsToPrint, printOptions, uiStore]);

  return (
    <ContextDialog open={showPrintDialog} onClose={handleClose} fullWidth maxWidth="lg">
      <DialogTitle>{t("print.dialog.title")}</DialogTitle>
      <DialogContent>
        <Box
          sx={{
            display: "flex",
            flexDirection: isSingleColumnLayout ? "column-reverse" : "row",
            alignItems: isSingleColumnLayout ? "center" : "stretch",
            width: "100%",
            justifyContent: "space-between",
          }}
        >
          <PrintOptionsWrapper
            itemsToPrint={itemsToPrint}
            printOptions={printOptions}
            setPrintOptions={setPrintOptions}
          />
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              m: 0.5,
              width: isSingleColumnLayout ? "100%" : "50%",
            }}
          >
            <HelperText />
            {imageLinks.length === itemsToPrint.length ? (
              <>
                {/* we preview only one item, resulting from choice of print options */}
                {printOptions.printIdentifierType === "IGSN" &&
                itemsToPrint.some((record) => record.identifiers.length === 0)
                  ? t("print.dialog.resolveError")
                  : Optional.fromNullable(itemsToPrint.at(0))
                      .map((inventoryRecord) => (
                        <PreviewPrintItem
                          key={inventoryRecord.globalId}
                          index={0}
                          printOptions={printOptions}
                          printLabelContents={{
                            itemLabel: `${toTitleCase(inventoryRecord.type)} - ${inventoryRecord.name}`,
                            locationLabel:
                              inventoryRecord instanceof ContainerModel || inventoryRecord instanceof SubSampleModel
                                ? (inventoryRecord.immediateParentContainer?.globalId ?? "-")
                                : "-",
                            identifier: Optional.fromNullable(inventoryRecord.identifiers.at(0)).map((identifier) => ({
                              doi: identifier.doi,
                            })),
                            globalId: Optional.fromNullable(inventoryRecord.globalId),
                            barcodeUrl: imageLinks[0],
                          }}
                          imageLinks={imageLinks}
                          target="screen"
                        />
                      ))
                      .orElseGet(() => {
                        throw new Error("Array is empty");
                      })}
                {/* we need the whole component for ReactToPrint, but not visible here */}
                <Box sx={{ display: "none" }}>
                  <PrintContents
                    ref={componentToPrint}
                    printOptions={printOptions}
                    itemsToPrint={itemsToPrint.map((record, index) => ({
                      itemLabel: `${toTitleCase(record.type)} - ${record.name}`,
                      locationLabel:
                        record instanceof ContainerModel || record instanceof SubSampleModel
                          ? (record.immediateParentContainer?.globalId ?? "-")
                          : "-",
                      identifier: Optional.fromNullable(record.identifiers.at(0)).map((identifier) => ({
                        doi: identifier.doi,
                      })),
                      globalId: Optional.fromNullable(record.globalId),
                      barcodeUrl: imageLinks[index],
                    }))}
                    imageLinks={imageLinks}
                    target={printOptions.printerType === "GENERIC" ? "multiplePrint" : "singlePrint"}
                  />
                </Box>
              </>
            ) : (
              <Typography variant="body2">{t("print.dialog.loading")}</Typography>
            )}
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          {t("actions.cancel", { ns: "common" })}
        </Button>
        <Button
          onClick={handlePrint}
          color="callToAction"
          variant="contained"
          disableElevation
          disabled={
            printOptions.printIdentifierType === "IGSN" &&
            itemsToPrint.some((record) => record.identifiers.length === 0)
          }
        >
          {t("print.dialog.printSelected", { count: itemsToPrint.length })}
        </Button>
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(PrintDialog);
