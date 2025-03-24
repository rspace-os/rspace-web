//@flow

import React, { forwardRef, type ComponentType } from "react";
import { type BarcodeRecord } from "../../../stores/definitions/Barcode";
import { type PrintOptions, type PrintType } from "./PrintDialog";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { toTitleCase } from "../../../util/Util";
import clsx from "clsx";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import ContainerModel from "../../../stores/models/ContainerModel";
import SubSampleModel from "../../../stores/models/SubSampleModel";

const itemPxWidths = {
  small: "110px",
  full: "220px",
};

const itemMmWidths = {
  small: "19mm",
  large: "38mm",
};

const useStyles = makeStyles()((theme) => ({
  // display mode of wrapper classes necessary for page breaks behaviour to work
  contentsWrapper: {
    // grid (not block) if generic printer
    margin: theme.spacing(0),
  },
  block: {
    display: "block",
  }, // for Zebra printing (no grid with page breaks)
  printItemWrapper: {
    display: "inline-block",
    border: "1px dotted #ccc",
    margin: theme.spacing(0.25),
    "@media print": {
      pageBreakInside: "avoid",
      img: { breakInside: "avoid" },
    },
  },
  /* for screen mode */
  smallPx: {
    width: itemPxWidths.small,
    maxWidth: itemPxWidths.small,
    padding: theme.spacing(0.25),
  },
  largePx: {
    width: itemPxWidths.full,
    maxWidth: itemPxWidths.full,
    padding: theme.spacing(0.25),
  },
  /* for grid/generic mode */
  smallMm: {
    width: itemMmWidths.small,
    maxWidth: itemMmWidths.small,
    padding: theme.spacing(0.25),
  },
  largeMm: {
    width: itemMmWidths.large,
    maxWidth: itemMmWidths.large,
    padding: theme.spacing(0.5),
  },
  /* for single/zebra mode */
  singlePc: {
    width: "90%",
    maxWidth: "90%",
    padding: theme.spacing(0.25),
  },
  bottomSpaced: {
    marginBottom: theme.spacing(0),
  },
  centeredSelf: {
    alignSelf: "center",
    marginTop: theme.spacing(0.25),
  },
  centeredText: {
    textAlign: "center",
  },
  wrappingText: {
    // "break-word" deprecated but seems to help safari 16 on mac
    wordBreak: "break-word",
  },
  smallText: { fontSize: "11px" },
  pageBreak: {
    "@media print": {
      breakAfter: "always",
      // on basic layout, singlePrint doesn't page-break without the following
      pageBreakAfter: "always",
    },
  },
}));

type Target = "screen" | "multiplePrint" | "singlePrint";

type PrintContentsArgs = {|
  printOptions: PrintOptions,
  printType: PrintType,
  itemsToPrint: $ReadOnlyArray<[BarcodeRecord, InventoryRecord]>, // LoM ...
  imageLinks?: Array<string>,
  target: Target,
|};

type PreviewPrintItemArgs = {|
  index: number,
  printOptions: PrintOptions,
  printType: PrintType,
  barcode: BarcodeRecord, // LoM ...

  /*
   * If `printOptions.printIdentifierType` is "IGSN", then `itemOwner` MUST
   * have at least one identifier. Do not render this component if it does not.
   */
  itemOwner: InventoryRecord,

  imageLinks?: Array<string>,
  forPrint?: boolean, // false for screen preview
  target: Target,
|};

export const PreviewPrintItem: ComponentType<PreviewPrintItemArgs> = ({
  index,
  printOptions,
  printType,
  barcode,
  itemOwner,
  imageLinks,
  target,
}) => {
  const isBarcodePrint = ["contextMenu", "barcodeLabel"].includes(printType);
  const { printerType, printLayout, printSize } = printOptions;

  const recordString = `${toTitleCase(itemOwner.type)} - ${itemOwner.name}`;

  const now = new Date();

  const { classes } = useStyles();
  const sizePerTarget = () =>
    target === "screen" && printSize === "SMALL"
      ? classes.smallPx
      : target === "screen" && printSize === "LARGE"
      ? classes.largePx
      : target === "multiplePrint" && printSize === "SMALL"
      ? classes.smallMm
      : target === "multiplePrint" && printSize === "LARGE"
      ? classes.largeMm
      : classes.singlePc; // no small and large for singlePrint case
  const header =
    printOptions.printIdentifierType === "GLOBAL ID"
      ? itemOwner.globalId
      : `IGSN ID: ${itemOwner.identifiers[0].doi}`;
  return (
    isBarcodePrint && (
      <>
        <div className={clsx(classes.printItemWrapper, sizePerTarget())}>
          <Grid
            container
            direction="column"
            spacing={1}
            className={classes.wrappingText}
          >
            {imageLinks && (
              <>
                <img
                  className={classes.centeredSelf}
                  src={imageLinks[index]}
                  title="Barcode Image"
                  width="75%"
                />
                <Grid
                  item
                  className={clsx(classes.centeredText, classes.bottomSpaced)}
                >
                  {target === "singlePrint" ? (
                    header
                  ) : (
                    <Typography variant={"h6"}>{header}</Typography>
                  )}
                </Grid>
              </>
            )}
            {printLayout === "FULL" && (
              <Grid item>
                <Grid
                  container
                  direction="column"
                  spacing={1}
                  className={clsx(classes.centeredText, classes.smallText)}
                >
                  <Grid item>{barcode.description.split("//")[1]}</Grid>
                  <Grid item>
                    <strong>Item:</strong>
                    <br />
                    {recordString}
                  </Grid>
                  <Grid item>
                    <strong>Location:</strong>{" "}
                    {itemOwner instanceof ContainerModel ||
                    itemOwner instanceof SubSampleModel
                      ? itemOwner.immediateParentContainer?.globalId || "-"
                      : "-"}
                  </Grid>
                  <Grid item>
                    <strong>Created:</strong>
                    <br />
                    {now.toLocaleString()}
                  </Grid>
                </Grid>
              </Grid>
            )}
          </Grid>
        </div>
        {/* force page break after item (when wrapper in display block) */}
        {printerType === "LABEL" && <div className={classes.pageBreak}></div>}
      </>
    )
  );
};

const PrintContents: ComponentType<PrintContentsArgs> = forwardRef(
  (
    {
      printOptions,
      printType,
      itemsToPrint,
      imageLinks,
      target,
    }: PrintContentsArgs,
    ref
  ) => {
    const { classes } = useStyles();
    return (
      <Grid
        ref={ref}
        container
        spacing={1}
        className={clsx(
          classes.contentsWrapper,
          // using display block to insert page breaks for label printers
          printOptions.printerType === "LABEL" && classes.block
        )}
      >
        {itemsToPrint.map(([barcode, itemOwner], i) => (
          <>
            <Grid item key={`${i}.1`}>
              <PreviewPrintItem
                index={i}
                printOptions={printOptions}
                printType={printType}
                barcode={barcode}
                itemOwner={itemOwner}
                imageLinks={imageLinks}
                target={target}
              />
            </Grid>
            {printOptions.printCopies === "2" && (
              <>
                <Grid item key={`${i}.2`}>
                  <PreviewPrintItem
                    index={i}
                    printOptions={printOptions}
                    printType={printType}
                    barcode={barcode}
                    itemOwner={itemOwner}
                    imageLinks={imageLinks}
                    target={target}
                  />
                </Grid>
                <Grid item sx={{ width: "100%" }}></Grid>
              </>
            )}
          </>
        ))}
        ;
      </Grid>
    );
  }
);

PrintContents.displayName = "PrintContents";
export default PrintContents;
