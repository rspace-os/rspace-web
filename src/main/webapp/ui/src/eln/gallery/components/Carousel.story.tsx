import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import Carousel from "./Carousel";
import createAccentedTheme from "@/accentedTheme";
import { Optional } from "@/util/optional";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { Description, LocalGalleryFile } from "../useGalleryListing";
import { incrementForever, take } from "@/util/iterators";
import { LandmarksProvider } from "@/components/LandmarksContext";

export function SimpleCarousel() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <Carousel
        listing={{
          tag: "list",
          list: [...take(incrementForever(), 8)].map(
            (i) =>
              new LocalGalleryFile({
                id: i,
                globalId: `GF${i}`,
                name: `Image${i}.jpg`,
                extension: "jpg",
                creationDate: new Date("2023-01-01T00:00:00Z"),
                modificationDate: new Date("2023-01-01T00:00:00Z"),
                description: Description.Empty(),
                type: "Image",
                ownerName: "user1",
                path: [],
                gallerySection: "Images",
                size: 0,
                version: 1,
                thumbnailId: null,
                originalImageId: null,
                token: "",
              })
          ),
          totalHits: 8,
          loadMore: Optional.empty<() => Promise<void>>(),
          refreshing: false,
        }}
        />
      </LandmarksProvider>
    </ThemeProvider>
  );
}
