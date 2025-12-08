import { MemoryRouter } from "react-router-dom";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { Gallery } from ".";
import type { GallerySection } from "./common";

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
        <LandmarksProvider>
            <MemoryRouter initialEntries={[`/gallery${urlSuffix ?? ""}`]}>
                <Gallery />
            </MemoryRouter>
        </LandmarksProvider>
    );
}
