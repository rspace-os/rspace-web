import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import Alerts from "@/components/Alerts/Alerts";
import { DialogBoundary } from "@/components/DialogBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import type { GalleryFile } from "@/eln/gallery/useGalleryListing";
import type Result from "@/util/result";
import type RsSet from "@/util/set";
import GalleryPicker from "../../eln/gallery/picker";

const GalleryEntrypoint = ({
    open,
    onClose = () => {},
    onSubmit,
    validateSelection,
}: {
    open: boolean;
    onClose?: () => void;
    onSubmit: (selection: RsSet<GalleryFile>) => void;
    validateSelection?: (file: GalleryFile) => Result<null>;
}) => (
    <StyledEngineProvider injectFirst>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Alerts>
                <DialogBoundary>
                    <MemoryRouter>
                        <LandmarksProvider>
                            <GalleryPicker
                                open={open}
                                onClose={onClose ?? (() => {})}
                                onSubmit={onSubmit}
                                validateSelection={validateSelection}
                            />
                        </LandmarksProvider>
                    </MemoryRouter>
                </DialogBoundary>
            </Alerts>
        </ThemeProvider>
    </StyledEngineProvider>
);

export default GalleryEntrypoint;
