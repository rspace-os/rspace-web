import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { incrementForever, take } from "@/util/iterators";
import { Optional } from "@/util/optional";
import type { GallerySection } from "../common";
import { Description, type GalleryFile, LocalGalleryFile } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import Carousel from "./Carousel";

// Selects the file before the carousel first renders, as when it opens on a file selected in another view.
function WithSelectedFile({ file, children }: { file: GalleryFile | undefined; children: React.ReactNode }) {
  const selection = useGallerySelection();
  React.useState(() => {
    if (file) selection.append(file);
  });
  return children;
}

function CarouselOfFiles({
  count,
  extension,
  type,
  gallerySection,
  selectedIndex,
}: {
  count: number;
  extension: string;
  type: string;
  gallerySection: GallerySection;
  selectedIndex?: number;
}) {
  const [list] = React.useState(() =>
    // ids start at 1: Aspose previews reject a falsy id
    [...take(incrementForever(), count)].map(
      (i) =>
        new LocalGalleryFile({
          id: i + 1,
          globalId: `GF${i + 1}`,
          name: `${type}${i + 1}.${extension}`,
          extension,
          creationDate: new Date("2023-01-01T00:00:00Z"),
          modificationDate: new Date("2023-01-01T00:00:00Z"),
          description: Description.Empty(),
          type,
          isSystemFolder: false,
          isSharedFolder: false,
          ownerId: 1,
          ownerName: "user1",
          ownerUsername: "user1",
          path: [],
          gallerySection,
          size: 0,
          version: 1,
          thumbnailId: null,
          originalImageId: null,
          metadata: {},
          token: "",
        }),
    ),
  );
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <GallerySelection>
          <WithSelectedFile file={selectedIndex === undefined ? undefined : list[selectedIndex]}>
            <Carousel
              listing={{
                tag: "list",
                list,
                totalHits: count,
                loadMore: Optional.empty<() => Promise<void>>(),
                refreshing: false,
              }}
            />
          </WithSelectedFile>
        </GallerySelection>
      </LandmarksProvider>
    </ThemeProvider>
  );
}

export function SimpleCarousel() {
  return <CarouselOfFiles count={8} extension="jpg" type="Image" gallerySection="Images" />;
}

export function CarouselOpenedOnFifthFile() {
  return <CarouselOfFiles count={8} extension="jpg" type="Image" gallerySection="Images" selectedIndex={4} />;
}

export function OfficeDocumentCarousel() {
  return <CarouselOfFiles count={4} extension="docx" type="Document" gallerySection="Documents" />;
}
