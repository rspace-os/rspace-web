import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { incrementForever, take } from "@/util/iterators";
import { Optional } from "@/util/optional";
import type { GallerySection } from "../common";
import { Description, LocalGalleryFile } from "../useGalleryListing";
import { GallerySelection } from "../useGallerySelection";
import Carousel from "./Carousel";

function CarouselOfFiles({
  count,
  extension,
  type,
  gallerySection,
}: {
  count: number;
  extension: string;
  type: string;
  gallerySection: GallerySection;
}) {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <GallerySelection>
          <Carousel
            listing={{
              tag: "list",
              // ids start at 1: Aspose previews reject a falsy id
              list: [...take(incrementForever(), count)].map(
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
              totalHits: count,
              loadMore: Optional.empty<() => Promise<void>>(),
              refreshing: false,
            }}
          />
        </GallerySelection>
      </LandmarksProvider>
    </ThemeProvider>
  );
}

export function SimpleCarousel() {
  return <CarouselOfFiles count={8} extension="jpg" type="Image" gallerySection="Images" />;
}

export function OfficeDocumentCarousel() {
  return <CarouselOfFiles count={4} extension="docx" type="Document" gallerySection="Documents" />;
}
