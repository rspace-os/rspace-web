import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import Alerts from "@/components/Alerts/Alerts";
import { DialogBoundary } from "@/components/DialogBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
// biome-ignore lint/style/useImportType: initial biome migration
import { GalleryFile } from "@/eln/gallery/useGalleryListing";
// biome-ignore lint/style/useImportType: initial biome migration
import Result from "@/util/result";
// biome-ignore lint/style/useImportType: initial biome migration
import RsSet from "@/util/set";
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
