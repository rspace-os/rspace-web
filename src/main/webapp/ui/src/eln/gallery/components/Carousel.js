//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { Optional } from "../../../util/optional";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { useGallerySelection } from "../useGallerySelection";

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
  const [zoom, setZoom] = React.useState(1);

  React.useEffect(() => {
    setVisibleIndex(0);
    selection.clear();
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
              setZoom((z) => z + 0.25);
            }}
          >
            Zoom In
          </Button>
        </Grid>
        <Grid item>
          <Button
            onClick={() => {
              setZoom((z) => z - 0.25);
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
      <Grid item flexGrow={1} sx={{ height: "calc(100% - 60px)" }}>
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
            <img
              src={f.isImage ? f.downloadHref : f.thumbnailUrl}
              style={{
                display: i === visibleIndex ? "block" : "none",
                maxHeight: "100%",
                maxWidth: "100%",
                transform: `scale(${zoom})`,
              }}
              key={idToString(f.id)}
            />
          ))}
        </div>
      </Grid>
    </Grid>
  );
}
