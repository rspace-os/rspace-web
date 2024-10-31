//@flow

import React, { type Node } from "react";
import { type GalleryFile } from "../useGalleryListing";
import { Optional } from "../../../util/optional";

type CarouselArgs = {
  listing:
    | {| tag: "empty", reason: string |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        loadMore: Optional<() => Promise<void>>,
      |},
};

export default function Carousel({ listing }: CarouselArgs) {
  if (listing.tag === "empty") return "No files";
  return listing.list.length;
}
