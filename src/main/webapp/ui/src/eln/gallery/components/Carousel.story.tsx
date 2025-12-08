import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { incrementForever, take } from "@/util/iterators";
import { Optional } from "@/util/optional";
import { Description, LocalGalleryFile } from "../useGalleryListing";
import Carousel from "./Carousel";

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
                                    metadata: {},
                                    token: "",
                                }),
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
