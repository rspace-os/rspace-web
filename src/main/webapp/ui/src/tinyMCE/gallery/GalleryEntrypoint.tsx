import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { DialogBoundary } from "@/components/DialogBoundary";
import GalleryPicker from "../../eln/gallery/picker";
import { MemoryRouter } from "react-router-dom";
import { LandmarksProvider } from "@/components/LandmarksContext";
import Alerts from "@/components/Alerts/Alerts";
import { GalleryFile } from "@/eln/gallery/useGalleryListing";
import RsSet from "@/util/set";
import Result from "@/util/result";

const GalleryEntrypoint = ({ open, onClose = () => {}, onSubmit, validateSelection }: {
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
)

export default GalleryEntrypoint