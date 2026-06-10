import React, { forwardRef } from "react";
import type { BarcodeRecord } from "@/stores/definitions/Barcode";
import { type PrintOptions } from "./PrintDialog";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { toTitleCase } from "@/util/Util";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import ContainerModel from "../../../../stores/models/ContainerModel";
import SubSampleModel from "../../../../stores/models/SubSampleModel";

const itemPxWidths = {
  small: "110px",
  full: "220px",
};

const itemMmWidths = {
  small: "19mm",
  large: "38mm",
};

const contentsWrapperSx = { m: 0 };
const blockSx = { display: "block" };
const smallPxSx = { width: itemPxWidths.small, maxWidth: itemPxWidths.small, p: 0.25 };
const largePxSx = { width: itemPxWidths.full, maxWidth: itemPxWidths.full, p: 0.25 };
const smallMmSx = { width: itemMmWidths.small, maxWidth: itemMmWidths.small, p: 0.25 };
const largeMmSx = { width: itemMmWidths.large, maxWidth: itemMmWidths.large, p: 0.5 };
const singlePcSx = { width: "90%", maxWidth: "90%", p: 0.25 };
const centeredTextSx = { textAlign: "center" };

type Target = "screen" | "multiplePrint" | "singlePrint";

type PrintContentsArgs = {
  printOptions: PrintOptions;
  itemsToPrint: Array<[BarcodeRecord, InventoryRecord]>; // LoM ...
  imageLinks?: Array<string>;
  target: Target;
};

type PreviewPrintItemArgs = {
  index: number;
  printOptions: PrintOptions;
  item: BarcodeRecord; // LoM ...
  itemOwner: InventoryRecord;
  imageLinks?: Array<string>;
  forPrint?: boolean; // false for screen preview
  target: Target;
};

export const PreviewPrintItem = ({
  index,
  printOptions,
  item,
  itemOwner,
  imageLinks,
  target,
}: PreviewPrintItemArgs) => {
  const { printerType, printLayout, printSize } = printOptions;

  const recordString = `${toTitleCase(itemOwner.type)} - ${itemOwner.name}`;

  const now = new Date();
  const sizePerTarget = () =>
    target === "screen" && printSize === "SMALL"
      ? smallPxSx
      : target === "screen" && printSize === "LARGE"
        ? largePxSx
        : target === "multiplePrint" && printSize === "SMALL"
          ? smallMmSx
          : target === "multiplePrint" && printSize === "LARGE"
            ? largeMmSx
            : singlePcSx; // no small and large for singlePrint case
  return (
    <>
      <Box
        component="div"
        sx={[
          {
            display: "inline-block",
            border: "1px dotted #ccc",
            m: 0.25,
            "@media print": {
              pageBreakInside: "avoid",
              img: { breakInside: "avoid" },
            },
          },
          sizePerTarget(),
        ]}
      >
        <Grid
          container
          sx={[{ wordBreak: "break-word" }, { flexDirection: "column" }]}
          spacing={1}
        >
          {imageLinks && (
            <>
              <Box
                component="img"
                sx={{ alignSelf: "center", mt: 0.25 }}
                src={imageLinks[index]}
                title="Barcode Image"
                alt="Barcode"
                width="75%"
              />
              <Grid sx={[centeredTextSx, { mb: 0 }]}>
                {target === "singlePrint" ? (
                  itemOwner.globalId
                ) : (
                  <Typography variant={"h6"}>{itemOwner.globalId}</Typography>
                )}
              </Grid>
            </>
          )}
          {printLayout === "FULL" && (
            <Grid>
              <Grid
                container
                sx={[centeredTextSx, { fontSize: "11px" }, { flexDirection: "column" }]}
                spacing={1}
              >
                <Grid>{item.description.split("//")[1]}</Grid>
                <Grid>
                  <strong>Item:</strong>
                  <br />
                  {recordString}
                </Grid>
                <Grid>
                  <strong>Location:</strong>{" "}
                  {itemOwner instanceof ContainerModel ||
                  itemOwner instanceof SubSampleModel
                    ? (itemOwner.immediateParentContainer?.globalId ?? "-")
                    : "-"}
                </Grid>
                <Grid>
                  <strong>Printed:</strong>
                  <br />
                  {now.toLocaleString()}
                </Grid>
              </Grid>
            </Grid>
          )}
        </Grid>
      </Box>
      {/* force page break after item (when wrapper in display block) */}
      {printerType === "LABEL" && (
        <Box
          component="div"
          sx={{ "@media print": { breakAfter: "always", pageBreakAfter: "always" } }}
        ></Box>
      )}
    </>
  );
};

const PrintContents = forwardRef<HTMLDivElement, PrintContentsArgs>(
  (
    { printOptions, itemsToPrint, imageLinks, target }: PrintContentsArgs,
    ref,
  ) => {
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
        {itemsToPrint.map(([item, itemOwner], i) => (
          <Grid key={i}>
            <PreviewPrintItem
              index={i}
              printOptions={printOptions}
              item={item}
              itemOwner={itemOwner}
              imageLinks={imageLinks}
              target={target}
            />
          </Grid>
        ))}
      </Grid>
    );
  },
);

PrintContents.displayName = "PrintContents";
export default PrintContents;
