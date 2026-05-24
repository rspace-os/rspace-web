import React, { forwardRef, Fragment } from "react";
import { type PrintOptions } from "./PrintDialog";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { Optional } from "../../../util/optional";
import { type GlobalId } from "../../../stores/definitions/BaseRecord";

export type PrintLabelContents = {
  itemLabel: string;
  locationLabel: string;
  identifier: Optional<{ doi: string }>;
  globalId: Optional<GlobalId>;
  barcodeUrl: string;
};

const itemPxWidth = {
  small: "110px",
  full: "220px",
};

const itemMmSize = {
  small: "19mm",
  large: "38mm",
};

const contentsWrapperSx = { m: 0 };
const blockSx = { display: "block" };
const printItemWrapperSx = {
  display: "inline-block",
  border: "1px dotted #ccc",
  m: 0.25,
  "@media print": {
    pageBreakInside: "avoid",
    img: { breakInside: "avoid" },
  },
};
const smallPxSx = { width: itemPxWidth.small, maxWidth: itemPxWidth.small, p: 0.25 };
const largePxSx = { width: itemPxWidth.full, maxWidth: itemPxWidth.full, p: 0.25 };
const smallPxHorzSx = { maxWidth: "47vw", p: 0.25 };
const largePxHorzSx = { maxWidth: "47vw", p: 0.25 };
const smallMmSx = { width: itemMmSize.small, maxWidth: itemMmSize.small, p: 0.25 };
const largeMmSx = { width: itemMmSize.large, maxWidth: itemMmSize.large, p: 0.5 };
const smallMmHorzSx = { maxWidth: "47vw", p: 0.25 };
const largeMmHorzSx = { maxWidth: "47vw", p: 0.5 };
const singlePcSx = { width: "90%", maxWidth: "90%", p: 0.25 };
const bottomSpacedSx = { mb: 0 };
const wrappingTextSx = { wordBreak: "break-word" };
const smallTextSx = { fontSize: "11px" };
const pageBreakSx = {
  "@media print": {
    breakAfter: "always",
    pageBreakAfter: "always",
  },
};

/*
 * "screen"        - for previewing print on screen
 * "multiplePrint" - for printing multiple items in a grid for a
 *                   regular printer
 * "singlePrint"   - for printing single items for a zebra printer
 */
type Target = "screen" | "multiplePrint" | "singlePrint";

type PrintContentsArgs = {
  printOptions: PrintOptions;
  itemsToPrint: ReadonlyArray<PrintLabelContents>;
  imageLinks?: ReadonlyArray<string>;
  target: Target;
};

type PreviewPrintItemArgs = {
  index: number;
  printOptions: PrintOptions;

  /*
   * If `printOptions.printIdentifierType` is "IGSN", then `printLabelContents`
   * SHOULD have a present identifier. If it does not then nothing will be
   * rendered.
   */
  printLabelContents: PrintLabelContents;

  imageLinks?: ReadonlyArray<string>;
  forPrint?: boolean; // false for screen preview
  target: Target;
};

export const PreviewPrintItem = ({
  index,
  printOptions,
  printLabelContents,
  imageLinks,
  target,
}: PreviewPrintItemArgs) => {
  const { printerType, printLayout, printSize } = printOptions;

  const now = new Date();

  const sizeClass = () => {
    if (target === "screen") {
      if (printOptions.printCopies === "2") {
        if (printSize === "SMALL") return smallPxHorzSx;
        if (printSize === "LARGE") return largePxHorzSx;
      }
      if (printSize === "SMALL") return smallPxSx;
      if (printSize === "LARGE") return largePxSx;
    }
    if (target === "multiplePrint") {
      if (printOptions.printCopies === "2") {
        if (printSize === "SMALL") return smallMmHorzSx;
        if (printSize === "LARGE") return largeMmHorzSx;
      }
      if (printSize === "SMALL") return smallMmSx;
      if (printSize === "LARGE") return largeMmSx;
    }
    // else singlePrint
    return singlePcSx;
  };

  const getHeader = () => {
    return printOptions.printIdentifierType === "GLOBAL ID"
      ? printLabelContents.globalId.map((globalId) => (
          <Fragment key={globalId}>
            <strong style={{ fontSize: "0.8em" }}>RSPACE GLOBAL ID</strong>
            <br />
            {globalId}
          </Fragment>
        ))
      : printLabelContents.identifier.map(({ doi }) => (
          <Fragment key={doi}>
            <strong style={{ fontSize: "0.8em" }}>IGSN ID</strong>
            <br />
            {doi}
          </Fragment>
        ));
  };

  const header = getHeader().orElse(null);

  if (!header) {
    return null;
  }

  return (
    <>
      <Box component="div" sx={[printItemWrapperSx, sizeClass()]}>
        <Grid
          container
          sx={[
            wrappingTextSx,
            {
              flexWrap: "nowrap",
              flexDirection: printOptions.printCopies === "2" ? "row" : "column",
            },
          ]}
        >
          {imageLinks && (
            <Grid>
              <img
                src={imageLinks[index]}
                title="Barcode Image"
                alt="Barcode"
                {...(printOptions.printCopies === "1"
                  ? { width: "75%" }
                  : { height: "80%" })}
              />
            </Grid>
          )}
          <Grid>
            <Grid
              container
              spacing={0.5}
                sx={[smallTextSx, { flexDirection: "column", lineHeight: "1.2", p: 1 }]}
            >
                <Grid sx={bottomSpacedSx}>
                {target === "singlePrint" ? (
                  header
                ) : (
                  <Typography
                    variant={"h6"}
                    sx={{
                      lineHeight: "1.1",
                    }}
                  >
                    {header}
                  </Typography>
                )}
              </Grid>
              {printLayout === "FULL" && (
                <>
                  {printLabelContents.globalId
                    .map((globalId) => (
                      <Grid
                        key={`global-id-${globalId}`}
                      >{`${window.location.origin}/globalId/${globalId}`}</Grid>
                    ))
                    .orElse(null)}
                  <Grid>
                    <strong>Item:</strong>{" "}
                    {printOptions.printCopies === "1" && <br />}
                    {printLabelContents.itemLabel}
                  </Grid>
                  <Grid>
                    <strong>Location:</strong>{" "}
                    {printLabelContents.locationLabel}
                  </Grid>
                  <Grid>
                    <strong>Printed:</strong>{" "}
                    {printOptions.printCopies === "1" && <br />}
                    {now.toLocaleString()}
                  </Grid>
                </>
              )}
            </Grid>
          </Grid>
        </Grid>
      </Box>
      {/* force page break after item (when wrapper in display block) */}
      {printerType === "LABEL" && <Box component="div" sx={pageBreakSx}></Box>}
    </>
  );
};

const PrintContents = forwardRef<HTMLDivElement, PrintContentsArgs>(
  ({ printOptions, itemsToPrint, imageLinks, target }, ref) => {
    return (
      <Grid
        ref={ref}
        container
        spacing={1}
        sx={
          printOptions.printerType === "LABEL"
            ? [contentsWrapperSx, blockSx]
            : contentsWrapperSx
        }
      >
        {itemsToPrint.map((itemToPrint, i) => (
          <Fragment key={`item-${i}`}>
            <Grid
              key={`${i}.1`}
              sx={
                printOptions.printCopies === "2" && target === "multiplePrint"
                  ? { width: "50vw" }
                  : {}
              }
            >
              <PreviewPrintItem
                index={i}
                printOptions={printOptions}
                printLabelContents={itemToPrint}
                imageLinks={imageLinks}
                target={target}
              />
            </Grid>
            {printOptions.printCopies === "2" && (
              <>
                <Grid key={`${i}.2`}>
                  <PreviewPrintItem
                    index={i}
                    printOptions={printOptions}
                    printLabelContents={itemToPrint}
                    imageLinks={imageLinks}
                    target={target}
                  />
                </Grid>
                <Grid sx={{ width: "100%" }}></Grid>
              </>
            )}
          </Fragment>
        ))}
        ;
      </Grid>
    );
  },
);

PrintContents.displayName = "PrintContents";
export default PrintContents;
