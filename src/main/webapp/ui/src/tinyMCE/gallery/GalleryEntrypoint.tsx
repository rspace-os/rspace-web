import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useContext } from "react";
import { MemoryRouter } from "react-router";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import Alerts from "@/components/Alerts/Alerts";
import { DialogBoundary } from "@/components/DialogBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import type { GalleryFile } from "@/eln/gallery/useGalleryListing";
import AlertContext, { type Alert } from "@/stores/contexts/Alert";
import type Result from "@/util/result";
import type RsSet from "@/util/set";
import GalleryPicker from "../../eln/gallery/picker";

const queryClient = new QueryClient();

/**
 * `onSubmit` is invoked by code outside this React tree (the TinyMCE plugin's
 * imperative generator function), so it has no way to reach `AlertContext`
 * itself. This bridge hands the tree's `addAlert` back out via `onAlertReady`
 * on every render, so that outside code can raise alerts into this tree's
 * own `<Alerts>` instance.
 */
function AlertBridge({ onAlertReady }: { onAlertReady?: (addAlert: (alert: Alert) => void) => void }) {
  const { addAlert } = useContext(AlertContext);
  onAlertReady?.(addAlert);
  return null;
}

const GalleryEntrypoint = ({
  open,
  onClose = () => {},
  onSubmit,
  validateSelection,
  onAlertReady,
}: {
  open: boolean;
  onClose?: () => void;
  onSubmit: (selection: RsSet<GalleryFile>) => void;
  validateSelection?: (file: GalleryFile) => Result<null>;
  onAlertReady?: (addAlert: (alert: Alert) => void) => void;
}) => (
  <QueryClientProvider client={queryClient}>
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <AlertBridge onAlertReady={onAlertReady} />
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
