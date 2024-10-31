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

  React.useEffect(() => {
    setVisibleIndex(0);
    selection.clear();
    selection.append(listing.list[0]);
  }, [listing]);

  if (listing.tag === "empty") return "No files";
  return (
    <Grid container direction="column" sx={{ height: "100%" }} spacing={2}>
      <Grid item container direction="row" spacing={1}>
        <Grid item>
          <Button
            onClick={() => {
              const newIndex = Math.max(0, visibleIndex - 1);
              setVisibleIndex(newIndex);
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
              const newIndex = Math.min(
                listing.list.length - 1,
                visibleIndex + 1
              );
              setVisibleIndex(newIndex);
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
          position: "relative",
        }}
      >
        {listing.list.map((f, i) => (
          <img
            src={f.isImage ? f.downloadHref : f.thumbnailUrl}
            style={{
              position: "absolute",
              display: i === visibleIndex ? "block" : "none",
              maxHeight: "100%",
              maxWidth: "100%",
            }}
            key={idToString(f.id)}
          />
        ))}
      </Grid>
    </Grid>
  );
}
