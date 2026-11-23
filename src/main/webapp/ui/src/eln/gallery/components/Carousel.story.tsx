import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
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

type FileKind = { extension: string; type: string; gallerySection: GallerySection };

const IMAGE: FileKind = { extension: "jpg", type: "Image", gallerySection: "Images" };
const OFFICE_DOCUMENT: FileKind = { extension: "docx", type: "Document", gallerySection: "Documents" };
const PDF: FileKind = { extension: "pdf", type: "Document", gallerySection: "Documents" };

function repeat(kind: FileKind, count: number): ReadonlyArray<FileKind> {
  return Array.from({ length: count }, () => kind);
}

function CarouselOfFiles({ files, selectedIndex }: { files: ReadonlyArray<FileKind>; selectedIndex?: number }) {
  const [list] = React.useState(() =>
    // ids start at 1: Aspose previews reject a falsy id
    files.map(
      ({ extension, type, gallerySection }, i) =>
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
                totalHits: files.length,
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
  return <CarouselOfFiles files={repeat(IMAGE, 8)} />;
}

export function CarouselOpenedOnFifthFile() {
  return <CarouselOfFiles files={repeat(IMAGE, 8)} selectedIndex={4} />;
}

export function OfficeDocumentCarousel() {
  return <CarouselOfFiles files={repeat(OFFICE_DOCUMENT, 4)} />;
}

// The carousel fills its parent, so a fixed height stands in for the Gallery's layout.
export function PdfCarousel() {
  return (
    <div style={{ height: "600px" }}>
      <CarouselOfFiles files={[IMAGE, PDF, PDF]} />
    </div>
  );
}
