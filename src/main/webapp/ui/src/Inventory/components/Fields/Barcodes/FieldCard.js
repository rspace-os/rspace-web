// @flow

import React, {
  useState,
  useEffect,
  type ElementProps,
  type Node,
} from "react";
import { type HasEditableFields } from "../../../../stores/definitions/Editable";
import { observer } from "mobx-react-lite";
import AddButton from "../../../../components/AddButton";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import { match, doNotAwait } from "../../../../util/Util";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import Badge from "@mui/material/Badge";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import Collapse from "@mui/material/Collapse";
import { withStyles } from "Styles";
import IconButton from "@mui/material/IconButton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Box from "@mui/material/Box";
import DeleteButton from "../../DeleteButton";
import { type BarcodeRecord } from "../../../../stores/definitions/Barcode";
import { type Factory } from "../../../../stores/definitions/Factory";
import Popover from "@mui/material/Popover";
import BarcodeScanner from "../../BarcodeScanner/BarcodeScanner";
import StringField from "../../../../components/Inputs/StringField";
import NoValue from "../../../../components/NoValue";
import useStores from "../../../../stores/use-stores";
import { mkAlert } from "../../../../stores/contexts/Alert";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import ImagePreview from "../../../../components/ImagePreview";
import PreviewIcon from "@mui/icons-material/Visibility";
import NoPreviewIcon from "@mui/icons-material/VisibilityOff";
import PrintDialog from "../../Print/PrintDialog";
import PrintIcon from "@mui/icons-material/Print";
import PrintDisabledIcon from "@mui/icons-material/PrintDisabled";
import Grid from "@mui/material/Grid";
import { barcodeFormatAsString } from "../../../../util/barcode";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import InputWrapper from "../../../../components/Inputs/InputWrapper";

const CustomCardHeader = withStyles<
  ElementProps<typeof CardHeader>,
  { root: string, action: string }
>((theme) => ({
  root: {
    padding: theme.spacing(0, 0, 0, 1.5),
  },
  action: {
    margin: 0,
  },
}))(CardHeader);

const DescriptionWrapper = withStyles<
  {| children: Node, isDeleted: boolean |},
  { root: string }
>((theme, { isDeleted }) => ({
  root: {
    textDecorationLine: isDeleted ? "line-through" : "none",
    wordBreak: "break-all",
  },
}))(({ classes, children }) => (
  <span className={classes.root}>{children}</span>
));

const CollapseContents = observer(
  <
    Fields: {
      barcodes: Array<BarcodeRecord>,
      ...
    },
    FieldOwner: HasEditableFields<Fields>
  >({
    fieldOwner,
    editable,
    connectedItem,
  }: {
    fieldOwner: FieldOwner,
    editable: boolean,
    connectedItem?: InventoryRecord,
  }): Node => {
    const { uiStore } = useStores();
    const barcodes = fieldOwner.fieldValues.barcodes;
    const imgUrlsAvailable =
      Boolean(connectedItem) && barcodes.every((b) => b.imageUrl);

    const remove = (b: BarcodeRecord) => {
      const index = barcodes.indexOf(b);
      const deleted = b.deletedCopy();
      if (!deleted) {
        const newBarcodes = ArrayUtils.splice(barcodes, index, 1);
        fieldOwner.setFieldsDirty({ barcodes: newBarcodes });
      } else {
        const newBarcodes = ArrayUtils.splice(barcodes, index, 1, deleted);
        fieldOwner.setFieldsDirty({ barcodes: newBarcodes });
      }
    };
    const changeDescription = (b: BarcodeRecord, value: string) => {
      b.setDescription(value);
      fieldOwner.setFieldsDirty({});
    };

    const [size, setSize] =
      useState<?{| width: number, height: number |}>(null);
    const [showPreview, setShowPreview] = useState(false);
    const [itemsToPrint, setItemsToPrint] = useState<
      Array<[BarcodeRecord, InventoryRecord]>
    >([]); // LoM...
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
      } catch (e) {
        uiStore.addAlert(
          mkAlert({
            title: "Unable to retrieve barcode image.",
            message: e.message || "",
            variant: "error",
            isInfinite: true,
          })
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
      } catch (e) {
        uiStore.addAlert(
          mkAlert({
            title: "Unable to retrieve barcode images.",
            message: e.message || "",
            variant: "error",
            isInfinite: true,
          })
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
        } catch (e) {
          uiStore.addAlert(
            mkAlert({
              title: "Unable to retrieve barcode image.",
              message: e.message ?? "",
              variant: "error",
            })
          );
        }
      }
    };

    return (
      <>
        <Box mt={1}>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Description</TableCell>
                  <TableCell>Actions</TableCell>
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
                              onChange={({ target: { value } }) =>
                                changeDescription(b, value)
                              }
                              variant="standard"
                              disabled={b.isDeleted}
                              noValueLabel="No description"
                            />
                          </InputWrapper>
                        ) : (
                          b.renderedDescription || (
                            <NoValue label="No description" />
                          )
                        )}
                      </DescriptionWrapper>
                    </TableCell>
                    <TableCell width={1}>
                      <Grid container direction="row" spacing={1} wrap="nowrap">
                        <Grid item>
                          <IconButtonWithTooltip
                            size="small"
                            color="primary"
                            title={
                              b.imageUrl && connectedItem?.canRead
                                ? "Print QR code"
                                : "Barcode print is not supported or you do not have permission."
                            }
                            icon={
                              b.imageUrl ? <PrintIcon /> : <PrintDisabledIcon />
                            }
                            disabled={
                              !connectedItem ||
                              !connectedItem.canRead ||
                              !b.imageUrl
                            }
                            onClick={doNotAwait(() => handlePrintOne(b))}
                          />
                        </Grid>
                        <Grid item>
                          <IconButtonWithTooltip
                            size="small"
                            color="primary"
                            title={
                              b.imageUrl
                                ? "Preview as QR code"
                                : "QR code preview is not supported."
                            }
                            icon={
                              b.imageUrl ? <PreviewIcon /> : <NoPreviewIcon />
                            }
                            disabled={!b.imageUrl}
                            onClick={doNotAwait(() => handlePreview(b))}
                          />
                        </Grid>
                        <Grid item>
                          <DeleteButton
                            onClick={() => remove(b)}
                            disabled={!editable || !b.isDeletable}
                            tooltipAfterClicked="Barcode will be deleted once this item is saved."
                            tooltipBeforeClicked="Remove"
                            tooltipWhenDisabled={
                              b.isDeletable
                                ? "First press Edit to remove this barcode."
                                : "Cannot delete generated barcodes"
                            }
                          />
                        </Grid>
                      </Grid>
                    </TableCell>
                  </TableRow>
                ))}
                {barcodes.length > 1 && (
                  <TableRow>
                    <TableCell>&nbsp;</TableCell>
                    <TableCell>
                      <IconButtonWithTooltip
                        size="small"
                        color="primary"
                        title={
                          imgUrlsAvailable && connectedItem?.canRead
                            ? "Print all barcodes"
                            : "Barcode print is not supported or you do not have permission."
                        }
                        icon={
                          imgUrlsAvailable ? (
                            <PrintIcon />
                          ) : (
                            <PrintDisabledIcon />
                          )
                        }
                        disabled={!imgUrlsAvailable}
                        onClick={doNotAwait(() => handlePrintAll())}
                      />
                      <span style={{ marginLeft: "8px" }}>Print All</span>
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
        {previewImages.length > 0 &&
          showPrintDialog &&
          Boolean(connectedItem) && (
            <PrintDialog
              showPrintDialog={showPrintDialog}
              printType="barcodeLabel"
              onClose={() => {
                if (previewImages.length > 0) {
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
  }
);

const ToggleButton = ({
  barcodeCount,
  open,
  setOpen,
}: {
  barcodeCount: number,
  open: boolean,
  setOpen: (boolean) => void,
}) => (
  <CustomTooltip
    title={match<void, string>([
      [() => barcodeCount === 0, "No current barcodes"],
      [() => open, "Hide barcodes listing"],
      [() => true, "Show barcodes listing"],
    ])()}
  >
    <IconButton onClick={() => setOpen(!open)} disabled={barcodeCount === 0}>
      <Badge color="primary" badgeContent={barcodeCount}>
        <ExpandCollapseIcon open={open} />
      </Badge>
    </IconButton>
  </CustomTooltip>
);

function FieldCard<
  Fields: {
    barcodes: Array<BarcodeRecord>,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  factory,
  connectedItem,
}: {|
  fieldOwner: FieldOwner,
  factory: Factory,
  connectedItem?: InventoryRecord,
|}): Node {
  const [open, setOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState<?EventTarget>(null);
  const { uiStore } = useStores();

  const editable = fieldOwner.isFieldEditable("barcodes");

  const barcodes = fieldOwner.fieldValues.barcodes;
  useEffect(() => {
    setOpen(barcodes.length > 0);
  }, [barcodes]);

  return (
    <Card variant="outlined">
      {(typeof connectedItem === "undefined" || connectedItem.canEdit) && (
        <CustomCardHeader
          subheader="Scan an existing barcode and associate it with this item."
          subheaderTypographyProps={{ variant: "body2" }}
          action={
            <>
              <AddButton
                disabled={!editable}
                onClick={({ currentTarget }) => setAnchorEl(currentTarget)}
                title={
                  editable
                    ? "Scan a barcode to associate."
                    : "Press Edit to scan a barcode."
                }
              />
              <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                onClose={() => setAnchorEl(null)}
                anchorOrigin={{
                  vertical: "center",
                  horizontal: "left",
                }}
                transformOrigin={{
                  vertical: "center",
                  horizontal: "right",
                }}
                PaperProps={{
                  variant: "outlined",
                  style: {
                    minWidth: 300,
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
                          title: "Unsupported barcode",
                          message: "Data is too long.",
                          variant: "error",
                        })
                      );
                      return;
                    }
                    fieldOwner.setFieldsDirty({
                      barcodes: [
                        ...barcodes,
                        factory.newBarcode({
                          data: barcode.rawValue,
                          newBarcodeRequest: true,
                          description: `Scanned ${barcodeFormatAsString(
                            barcode.format
                          )}: ${barcode.rawValue}`,
                        }),
                      ],
                    });
                  }}
                  buttonPrefix="Save"
                />
              </Popover>
              <ToggleButton
                barcodeCount={barcodes.length}
                open={open}
                setOpen={setOpen}
              />
            </>
          }
        />
      )}
      <Collapse in={open}>
        <CollapseContents
          editable={editable}
          fieldOwner={fieldOwner}
          connectedItem={connectedItem}
        />
      </Collapse>
    </Card>
  );
}

export default (observer(FieldCard): typeof FieldCard);
