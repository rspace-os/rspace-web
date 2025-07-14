import React from "react";
import { Gallery } from ".";
import { MemoryRouter } from "react-router-dom";
import { GallerySection } from "./common";

/**
 * Not exactly a reusable component, but a story that shows the different URL
 * patterns that the Gallery expects.
 */
export function GalleryStory({
  urlSuffix,
}: {
  urlSuffix?:
    | `?mediaType=${GallerySection}` // The roots of each Gallery section
    | `/${number}` // the id of a folder
    | `/item/${number}`; // the id of a file
}) {
  return (
    <MemoryRouter initialEntries={[`/gallery${urlSuffix ?? ""}`]}>
      <Gallery />
    </MemoryRouter>
  );
}
