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
import { useCallback, useRef, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useReactToPrint } from "react-to-print";
import docLinks from "../../../../assets/DocLinks";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { BarcodeRecord } from "../../../../stores/definitions/Barcode";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import useStores from "../../../../stores/use-stores";
import ContextDialog from "../../ContextMenu/ContextDialog";
import { useIsSingleColumnLayout } from "../../Layout/Layout2x1";
import PrintContents, { PreviewPrintItem } from "./PrintContents";

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

export const PrintOptionsWrapper = ({ printOptions, setPrintOptions }: OptionsWrapperArgs) => {
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
          <FormLabel id="print-size-radiogroup-label">{t("print.options.printSize")}</FormLabel>
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
              <FormControlLabel value="LARGE" control={<Radio size="small" />} label={t("print.options.large")} />
              <FormControlLabel value="SMALL" control={<Radio size="small" />} label={t("print.options.small")} />
            </RadioGroup>
          )}
          <Alert severity="info">
            {printOptions.printerType === "LABEL"
              ? t("print.options.labelPrinterAutoSize")
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
  imageLinks,
  itemsToPrint,
  printerType,
  printSize,
  closeMenu,
}: PrintDialogArgs) {
  const { t } = useTranslation(["inventory", "common"]);
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const componentToPrint = useRef<HTMLDivElement>(null);

  const [item, itemOwner] = itemsToPrint[0];

  const [printOptions, setPrintOptions] = useState<PrintOptions>({
    printerType: printerType ?? "GENERIC",
    printLayout: "FULL",
    printSize: printSize ?? "LARGE",
  });

  const handleClose = useCallback(() => {
    onClose();
    if (typeof closeMenu === "function") closeMenu();
  }, [onClose, closeMenu]);

  const handlePrint = useReactToPrint({
    contentRef: componentToPrint,
    onAfterPrint: () => {
      handleClose();
    },
    onPrintError: (_errorLocation, error) => {
      uiStore.addAlert(
        mkAlert({
          title: t("print.dialog.printError"),
          message: error.message || "",
          variant: "error",
          isInfinite: true,
        }),
      );
    },
  });

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
            <Typography variant="body2" sx={{ textAlign: "center", mb: 1 }}>
              <strong>{t("print.dialog.previewLabelLayout")}</strong>
            </Typography>
            {/* we preview only one item, resulting from choice of print options */}
            <PreviewPrintItem
              index={0}
              printOptions={printOptions}
              item={item}
              itemOwner={itemOwner}
              imageLinks={imageLinks}
              target="screen"
            />
          </Box>
          {/* we need the whole component for ReactToPrint, but not visible here */}
          <Box sx={{ display: "none" }}>
            <PrintContents
              ref={componentToPrint}
              printOptions={printOptions}
              itemsToPrint={itemsToPrint}
              imageLinks={imageLinks}
              target={printOptions.printerType === "GENERIC" ? "multiplePrint" : "singlePrint"}
            />
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          {t("actions.cancel", { ns: "common" })}
        </Button>
        <Button onClick={handlePrint} color="callToAction" variant="contained" disableElevation disabled={false}>
          {t("print.dialog.printSelected", { count: itemsToPrint.length })}
        </Button>
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(PrintDialog);
