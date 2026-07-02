import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import Alerts from "@/components/Alerts/Alerts";
import { DialogBoundary } from "@/components/DialogBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import type { GalleryFile } from "@/eln/gallery/useGalleryListing";
import type Result from "@/util/result";
import type RsSet from "@/util/set";
import GalleryPicker from "../../eln/gallery/picker";

const queryClient = new QueryClient();

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
  <QueryClientProvider client={queryClient}>
    <StyledEngineProvider injectFirst enableCssLayer>
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
  </QueryClientProvider>
);

export default GalleryEntrypoint;
