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
import React, { useCallback, useRef, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useReactToPrint } from "react-to-print";
import docLinks from "../../../assets/DocLinks";
import ApiService from "../../../common/InvApiService";
import { mkAlert } from "../../../stores/contexts/Alert";
import useStores from "../../../stores/use-stores";
import { Optional } from "../../../util/optional";
import ContextDialog from "../../components/ContextMenu/ContextDialog";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import PrintContents, { PreviewPrintItem } from "../../components/Print/PrintContents";
import type { Identifier } from "../../useIdentifiers";

export type PrinterType = "GENERIC" | "LABEL";
export type PrintLayout = "BASIC" | "FULL";
export type PrintSize = "SMALL" | "LARGE";

export type PrintOptions = {
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
  printIdentifierType?: "IGSN";
};

type PrintDialogArgs = {
  showPrintDialog: boolean;
  onClose: () => void;
  itemsToPrint: ReadonlyArray<Identifier>;
  printerType?: PrinterType;
  printSize?: PrintSize;
  /* n/a for non-contextMenu cases */
  closeMenu?: () => void;
};

type OptionsWrapperArgs = {
  printOptions: PrintOptions;
  setPrintOptions: (options: PrintOptions) => void;
};

export const PrintOptionsWrapper = ({ printOptions, setPrintOptions }: OptionsWrapperArgs): React.ReactNode => {
  const { t } = useTranslation("inventory");
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <FormControl component="fieldset" sx={{ width: isSingleColumnLayout ? "100%" : "50%" }}>
      <Stack spacing={3}>
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
              <Trans
                ns="inventory"
                i18nKey="print.options.labelShapeHint"
                components={{
                  // biome-ignore lint/a11y/useAnchorContent: Trans component template element, content is injected by Trans
                  a: <a href={docLinks.barcodesPrinting} target="_blank" rel="noreferrer" />,
                }}
              />
            </Alert>
          )}
        </FormControl>
        <FormControl>
          <FormLabel id="print-copties-radiogroup-label">{t("print.options.printCopies")}</FormLabel>
          {printOptions.printerType === "GENERIC" && (
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
              <FormControlLabel
                value="2"
                control={<Radio size="small" />}
                label={t("print.options.eachBarcodeTwice")}
              />
            </RadioGroup>
          )}
          {printOptions.printerType === "LABEL" && (
            <Alert severity="info">{t("print.options.labelPrinterCopies")}</Alert>
          )}
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
}: PrintDialogArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { uiStore, trackingStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<HTMLDivElement>(null);

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
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
        printIdentifierType: "IGSN ID",
        printerType: printOptions.printerType,
        printLayout: printOptions.printLayout,
        printSize: printOptions.printSize,
        printCopies: printOptions.printCopies,
      });
    },
    onPrintError: (e: Error | string) => {
      uiStore.addAlert(
        mkAlert({
          title: t("print.dialog.printError"),
          message: typeof e === "object" ? e.message || "" : String(e),
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
    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    imageLinks.forEach((img) => URL.revokeObjectURL(img));

    const getImageUrl = async (identifier: Identifier) => {
      const { data } = await ApiService.query<Blob>(
        "/barcodes",
        new URLSearchParams({
          content: `https://doi.org/${identifier.doi}`,
          barcodeType: "QR",
        }),
        true,
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
            title: t("print.dialog.unableToRetrieveBarcodeImages"),
            message: e.message || "",
            variant: "error",
            isInfinite: true,
          }),
        );
      });
  }, [itemsToPrint, uiStore]);

  return (
    <ContextDialog open={showPrintDialog} onClose={handleClose} fullWidth maxWidth="lg">
      <DialogTitle>{t("print.dialog.title")}</DialogTitle>
      <DialogContent>
        <Box
          sx={
            isSingleColumnLayout
              ? {
                  display: "flex",
                  flexDirection: "column-reverse",
                  alignItems: "center",
                  width: "100%",
                }
              : {
                  display: "flex",
                  flexDirection: "row",
                  width: "100%",
                  justifyContent: "space-between",
                }
          }
        >
          <PrintOptionsWrapper printOptions={printOptions} setPrintOptions={setPrintOptions} />
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
                {Optional.fromNullable(itemsToPrint.at(0))
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
                  .orElse(t("print.dialog.nothingToPreview"))}
                {/* we need the whole component for ReactToPrint, but not visible here */}
                <Box sx={{ display: "none" }}>
                  <PrintContents
                    ref={componentToPrint}
                    printOptions={{
                      ...printOptions,
                      printIdentifierType: "IGSN",
                    }}
                    itemsToPrint={itemsToPrint.map((identifier, index) => ({
                      itemLabel: "-",
                      locationLabel: "-",
                      identifier: Optional.present(identifier),
                      globalId: Optional.empty(),
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
        <Button onClick={handlePrint} color="callToAction" variant="contained" disableElevation>
          {t("print.dialog.printSelected", { count: itemsToPrint.length })}
        </Button>
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(PrintDialog);
