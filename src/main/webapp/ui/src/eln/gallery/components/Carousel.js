//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { Optional } from "../../../util/optional";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { useGallerySelection } from "../useGallerySelection";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";
import usePrimaryAction from "../primaryActionHooks";

/*
 * Arbitrary number that determines how much the zoom in and out buttons zoom
 * in and out with each tap. Too small of a number and the user has to tap over
 * and over to reach the level of desired zoom, to much and the user doesn't
 * have sufficiently fine-grained control.
 */
const ZOOM_SCALE_FACTOR = 1.4;

type CarouselArgs = {
  listing:
    | {| tag: "empty", reason: string |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        loadMore: Optional<() => Promise<void>>,
      |},
};

export default function Carousel({ listing }: CarouselArgs): Node {
  const [visibleIndex, setVisibleIndex] = React.useState(0);
  const selection = useGallerySelection();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();
  const primaryAction = usePrimaryAction();
  const [zoom, setZoom] = React.useState(1);

  React.useEffect(() => {
    setVisibleIndex(0);
    selection.clear();
    //$FlowExpectedError[incompatible-use] We need some kind of non-empty list data structure
    selection.append(listing.list[0]);
  }, [listing]);

  if (listing.tag === "empty") return "No files";
  return (
    <Grid
      container
      direction="column"
      sx={{ height: "100%" }}
      spacing={2}
      flexWrap="nowrap"
    >
      <Grid item container direction="row" spacing={1}>
        <Grid item>
          <Button
            onClick={() => {
              const newIndex = Math.max(0, visibleIndex - 1);
              setVisibleIndex(newIndex);
              setZoom(1);
              selection.clear();
              selection.append(listing.list[newIndex]);
            }}
          >
            Previous
          </Button>
        </Grid>
        <Grid item flexGrow={1}></Grid>
        <Grid item>
          <Button
            onClick={() => {
              setZoom((z) => z * ZOOM_SCALE_FACTOR);
            }}
          >
            Zoom In
          </Button>
        </Grid>
        <Grid item>
          <Button
            onClick={() => {
              setZoom((z) => z / ZOOM_SCALE_FACTOR);
            }}
          >
            Zoom Out
          </Button>
        </Grid>
        <Grid item flexGrow={1}></Grid>
        <Grid item>
          <Button
            onClick={() => {
              const newIndex = Math.min(
                listing.list.length - 1,
                visibleIndex + 1
              );
              setVisibleIndex(newIndex);
              setZoom(1);
              selection.clear();
              selection.append(listing.list[newIndex]);
            }}
          >
            Next
          </Button>
        </Grid>
      </Grid>
      <Grid
        item
        flexGrow={1}
        sx={{
          /*
           * This minHeight is necessary to ensure that the image and wrapping
           * div shrink so that they don't cause scrollbars. Once the user
           * initiates a zoom then there will be scrollbars, but not before.
           * The need for a minHeight of 0 is explained in
           * https://moduscreate.com/blog/how-to-fix-overflow-issues-in-css-flex-layouts/
           * which references the "Automatic Minimum Size of Flex Items" part of
           * the flexbox spec:
           * https://drafts.csswg.org/css-flexbox/#min-size-auto
           */
          minHeight: "0",
        }}
      >
        <div
          style={{
            borderRadius: "3px",
            border: "2px solid #d0cad4",
            position: "relative",
            height: "100%",
            overflow: "auto",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          {listing.list.map((f, i) => (
            /*
             * It doesn't matter that this is an accessibility violation
             * because the same functionality is available in the info panel
             */
            //eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
            <img
              src={f.isImage ? f.downloadHref : f.thumbnailUrl}
              style={{
                display: i === visibleIndex ? "block" : "none",
                maxHeight: "100%",
                maxWidth: "100%",
                transform: `scale(${zoom})`,
                transition: "transform .5s ease-in-out",
                transformOrigin: "left top",
              }}
              key={idToString(f.id)}
              onClick={(e) => {
                if (e.detail > 1) {
                  primaryAction(f).do((action) => {
                    if (action.tag === "open") {
                      action.open();
                      return;
                    }
                    if (action.tag === "image") {
                      openImagePreview(action.downloadHref);
                      return;
                    }
                    if (action.tag === "collabora") {
                      window.open(action.url);
                      return;
                    }
                    if (action.tag === "officeonline") {
                      window.open(action.url);
                      return;
                    }
                    if (action.tag === "pdf") {
                      openPdfPreview(action.downloadHref);
                      return;
                    }
                    if (action.tag === "aspose") {
                      void openAsposePreview(f);
                    }
                  });
                }
              }}
            />
          ))}
        </div>
      </Grid>
    </Grid>
  );
}
