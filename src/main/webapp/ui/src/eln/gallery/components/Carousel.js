//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { Optional } from "../../../util/optional";
import Button from "@mui/material/Button";

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

  if (listing.tag === "empty") return "No files";
  return (
    <>
      <Button
        onClick={() => {
          setVisibleIndex((v) => Math.max(0, v - 1));
        }}
      >
        Previous
      </Button>
      <Button
        onClick={() => {
          setVisibleIndex((v) => Math.min(listing.list.length - 1, v + 1));
        }}
      >
        Next
      </Button>
      <div
        style={{
          position: "relative",
        }}
      >
        {listing.list
          .filter((f) => f.isImage)
          .map((f, i) => (
            <img
              src={f.downloadHref}
              style={{
                position: "absolute",
                display: i === visibleIndex ? "block" : "none",
                width: "100%",
              }}
              key={idToString(f.id)}
            />
          ))}
      </div>
    </>
  );
}
