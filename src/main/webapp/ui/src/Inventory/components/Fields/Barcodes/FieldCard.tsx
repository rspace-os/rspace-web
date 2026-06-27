import PrintIcon from "@mui/icons-material/Print";
import PrintDisabledIcon from "@mui/icons-material/PrintDisabled";
import PreviewIcon from "@mui/icons-material/Visibility";
import NoPreviewIcon from "@mui/icons-material/VisibilityOff";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import Collapse from "@mui/material/Collapse";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Popover from "@mui/material/Popover";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import { type ReactNode, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AddButton from "../../../../components/AddButton";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import ImagePreview from "../../../../components/ImagePreview";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import StringField from "../../../../components/Inputs/StringField";
import NoValue from "../../../../components/NoValue";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { BarcodeRecord } from "../../../../stores/definitions/Barcode";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { Factory } from "../../../../stores/definitions/Factory";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import useStores from "../../../../stores/use-stores";
import { barcodeFormatAsString } from "../../../../util/barcode";
import { match } from "../../../../util/Util";
import BarcodeScanner from "../../BarcodeScanner/BarcodeScanner";
import DeleteButton from "../../DeleteButton";
import PrintDialog from "./PrintDialog";

function DescriptionWrapper({ children, isDeleted }: { children: ReactNode; isDeleted: boolean }): ReactNode {
  return (
    <Box
      component="span"
      sx={{
        textDecorationLine: isDeleted ? "line-through" : "none",
        wordBreak: "break-all",
      }}
    >
      {children}
    </Box>
  );
}
const CollapseContents = observer(
  <
    Fields extends {
      barcodes: Array<BarcodeRecord>;
    },
    FieldOwner extends HasEditableFields<Fields>,
  >({
    fieldOwner,
    editable,
    connectedItem,
  }: {
    fieldOwner: FieldOwner;
    editable: boolean;
    connectedItem?: InventoryRecord;
  }): ReactNode => {
    const { t } = useTranslation("inventory");
    const { uiStore } = useStores();
    const barcodes = fieldOwner.fieldValues.barcodes;
    const imgUrlsAvailable = Boolean(connectedItem) && barcodes.every((b) => b.imageUrl);
    const remove = (b: BarcodeRecord) => {
      const index = barcodes.indexOf(b);
      const deleted = b.deletedCopy();
      if (!deleted) {
        const newBarcodes = barcodes.toSpliced(index, 1);
        fieldOwner.setFieldsDirty({
          barcodes: newBarcodes,
        });
      } else {
        const newBarcodes = barcodes.toSpliced(index, 1, deleted);
        fieldOwner.setFieldsDirty({
          barcodes: newBarcodes,
        });
      }
    };
    const changeDescription = (b: BarcodeRecord, value: string) => {
      b.setDescription(value);
      fieldOwner.setFieldsDirty({});
    };
    const [size, setSize] = useState<{
      width: number;
      height: number;
    } | null>(null);
    const [showPreview, setShowPreview] = useState(false);
    const [itemsToPrint, setItemsToPrint] = useState<Array<[BarcodeRecord, InventoryRecord]>>([]);
    const [previewImages, setPreviewImages] = useState<Array<string>>([]);
    const [showPrintDialog, setShowPrintDialog] = useState(false);
    const handlePrintOne = async (barcode: BarcodeRecord): Promise<void> => {
      try {
        if (!connectedItem) throw new Error("Printing not supported");
        if (barcode.imageUrl) {
          setItemsToPrint([[barcode, connectedItem]]);
          const image = await barcode.fetchImage();
          if (image) setPreviewImages([URL.createObjectURL(image)]);
        }
      } catch (e: unknown) {
        uiStore.addAlert(
          mkAlert({
            title: t("fields.barcodes.alerts.unableToRetrieveBarcodeImage"),
            message: e instanceof Error ? e.message : String(e),
            variant: "error",
            isInfinite: true,
          }),
        );
      } finally {
        setShowPrintDialog(true);
      }
    };
    const handlePrintAll = async (): Promise<void> => {
      try {
        if (!connectedItem) throw new Error("Printing not supported");
        if (imgUrlsAvailable) {
          setItemsToPrint(barcodes.map((b) => [b, connectedItem]));
          const images = await Promise.all(barcodes.map((b) => b.fetchImage()));
          const imageUrls = images.map((img) => URL.createObjectURL(img));
          setPreviewImages(imageUrls);
        }
      } catch (e: unknown) {
        uiStore.addAlert(
          mkAlert({
            title: t("fields.barcodes.alerts.unableToRetrieveBarcodeImages"),
            message: e instanceof Error ? e.message : String(e),
            variant: "error",
            isInfinite: true,
          }),
        );
      } finally {
        setShowPrintDialog(true);
      }
    };
    const handlePreview = async (barcode: BarcodeRecord): Promise<void> => {
      if (barcode.imageUrl) {
        try {
          const image = await barcode.fetchImage();
          setPreviewImages([URL.createObjectURL(image)]);
          setShowPreview(true);
        } catch (e: unknown) {
          uiStore.addAlert(
            mkAlert({
              title: "Unable to retrieve barcode image.",
              message: e instanceof Error ? e.message : String(e),
              variant: "error",
            }),
          );
        }
      }
    };
    return (
      <>
        <Box
          sx={{
            mt: 1,
          }}
        >
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t("fields.barcodes.columns.description")}</TableCell>
                  <TableCell>{t("fields.barcodes.columns.actions")}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {barcodes.map((b, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <DescriptionWrapper isDeleted={b.isDeleted}>
                        {editable && b.descriptionIsEditable ? (
                          <InputWrapper
                            maxLength={255}
                            disabled={b.isDeleted}
                            error={(b.description ?? "").length > 255}
                            value={b.description ?? ""}
                          >
                            <StringField
                              error={(b.description ?? "").length > 255}
                              value={b.description ?? ""}
                              onChange={({ target: { value } }) => changeDescription(b, value)}
                              variant="standard"
                              disabled={b.isDeleted}
                              noValueLabel={t("fields.barcodes.noDescription")}
                            />
                          </InputWrapper>
                        ) : (
                          b.renderedDescription || <NoValue label={t("fields.barcodes.noDescription")} />
                        )}
                      </DescriptionWrapper>
                    </TableCell>
                    <TableCell width={1}>
                      <Grid container direction="row" spacing={1} sx={{ flexWrap: "nowrap" }}>
                        <Grid>
                          <IconButtonWithTooltip
                            size="small"
                            color="primary"
                            title={
                              b.imageUrl && connectedItem?.canRead
                                ? t("fields.barcodes.actions.printQrCode")
                                : t("fields.barcodes.actions.printUnsupported")
                            }
                            icon={b.imageUrl ? <PrintIcon /> : <PrintDisabledIcon />}
                            disabled={!connectedItem?.canRead || !b.imageUrl}
                            onClick={() => void handlePrintOne(b)}
                          />
                        </Grid>
                        <Grid>
                          <IconButtonWithTooltip
                            size="small"
                            color="primary"
                            title={
                              b.imageUrl
                                ? t("fields.barcodes.actions.previewQrCode")
                                : t("fields.barcodes.actions.previewUnsupported")
                            }
                            icon={b.imageUrl ? <PreviewIcon /> : <NoPreviewIcon />}
                            disabled={!b.imageUrl}
                            onClick={() => void handlePreview(b)}
                          />
                        </Grid>
                        <Grid>
                          <DeleteButton
                            onClick={() => remove(b)}
                            disabled={!editable || !b.isDeletable}
                            tooltipAfterClicked={t("fields.barcodes.actions.deleteAfterClicked")}
                            tooltipBeforeClicked={t("fields.barcodes.actions.remove")}
                            tooltipWhenDisabled={
                              b.isDeletable
                                ? t("fields.barcodes.actions.removeNeedsEdit")
                                : t("fields.barcodes.actions.cannotDeleteGenerated")
                            }
                          />
                        </Grid>
                      </Grid>
                    </TableCell>
                  </TableRow>
                ))}
                {barcodes.length > 1 && (
                  <TableRow>
                    <TableCell> </TableCell>
                    <TableCell>
                      <IconButtonWithTooltip
                        size="small"
                        color="primary"
                        title={
                          imgUrlsAvailable && connectedItem?.canRead
                            ? t("fields.barcodes.actions.printAllBarcodes")
                            : t("fields.barcodes.actions.printUnsupported")
                        }
                        icon={imgUrlsAvailable ? <PrintIcon /> : <PrintDisabledIcon />}
                        disabled={!imgUrlsAvailable}
                        onClick={() => void handlePrintAll()}
                      />
                      <Box
                        component="span"
                        sx={{
                          marginLeft: "8px",
                        }}
                      >
                        {t("fields.barcodes.actions.printAll")}
                      </Box>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
        {previewImages.length > 0 && showPreview && (
          <ImagePreview
            closePreview={() => {
              if (previewImages[0]) {
                URL.revokeObjectURL(previewImages[0]);
                setShowPreview(false);
              }
            }}
            link={previewImages[0]}
            size={size}
            setSize={setSize}
          />
        )}
        {previewImages.length > 0 && showPrintDialog && Boolean(connectedItem) && (
          <PrintDialog
            showPrintDialog={showPrintDialog}
            onClose={() => {
              if (previewImages.length > 0) {
                // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
                previewImages.forEach((pi) => URL.revokeObjectURL(pi));
                setShowPrintDialog(false);
              }
            }}
            imageLinks={previewImages}
            itemsToPrint={itemsToPrint}
          />
        )}
      </>
    );
  },
);
function FieldCard<
  Fields extends {
    barcodes: Array<BarcodeRecord>;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  fieldOwner,
  factory,
  connectedItem,
}: {
  fieldOwner: FieldOwner;
  factory: Factory;
  connectedItem?: InventoryRecord;
}): ReactNode {
  const [open, setOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<EventTarget | null>(null);
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();
  const editable = fieldOwner.isFieldEditable("barcodes");
  const barcodes = fieldOwner.fieldValues.barcodes;
  useEffect(() => {
    setOpen(barcodes.length > 0);
  }, [barcodes]);
  return (
    <Card variant="outlined">
      {(typeof connectedItem === "undefined" || connectedItem.canEdit) && (
        <CardHeader
          sx={{ p: "0 0 0 12px" }}
          slotProps={{
            action: { sx: { m: 0 } },
            subheader: {
              variant: "body2",
            },
          }}
          subheader={t("fields.barcodes.summary")}
          action={
            <>
              <AddButton
                disabled={!editable}
                onClick={({ currentTarget }) => setAnchorEl(currentTarget)}
                title={editable ? t("fields.barcodes.actions.scan") : t("fields.barcodes.actions.scanNeedsEdit")}
              />
              <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl as HTMLElement}
                onClose={() => setAnchorEl(null)}
                anchorOrigin={{
                  vertical: "center",
                  horizontal: "left",
                }}
                transformOrigin={{
                  vertical: "center",
                  horizontal: "right",
                }}
                slotProps={{
                  paper: {
                    variant: "outlined",
                    style: {
                      minWidth: 300,
                    },
                  },
                }}
              >
                <BarcodeScanner
                  onClose={() => {
                    setAnchorEl(null);
                  }}
                  onScan={(barcode) => {
                    if (barcode.rawValue.length > 255) {
                      uiStore.addAlert(
                        mkAlert({
                          title: t("fields.barcodes.alerts.unsupportedBarcode"),
                          message: t("fields.barcodes.alerts.dataTooLong"),
                          variant: "error",
                        }),
                      );
                      return;
                    }
                    fieldOwner.setFieldsDirty({
                      barcodes: [
                        ...barcodes,
                        factory.newBarcode({
                          data: barcode.rawValue,
                          newBarcodeRequest: true,
                          description: t("fields.barcodes.scannedDescription", {
                            format: barcodeFormatAsString(barcode.format),
                            value: barcode.rawValue,
                          }),
                        }),
                      ],
                    });
                  }}
                  buttonPrefix={t("fields.barcodes.actions.save")}
                />
              </Popover>
              <CustomTooltip
                title={match<void, string>([
                  [() => barcodes.length === 0, t("fields.barcodes.toggle.none")],
                  [() => open, t("fields.barcodes.toggle.hide")],
                  [() => true, t("fields.barcodes.toggle.show")],
                ])()}
              >
                <IconButton onClick={() => setOpen(!open)} disabled={barcodes.length === 0}>
                  <Badge color="primary" badgeContent={barcodes.length}>
                    <ExpandCollapseIcon open={open} />
                  </Badge>
                </IconButton>
              </CustomTooltip>
            </>
          }
        />
      )}
      <Collapse in={open}>
        <CollapseContents editable={editable} fieldOwner={fieldOwner} connectedItem={connectedItem} />
      </Collapse>
    </Card>
  );
}
export default FieldCard;
