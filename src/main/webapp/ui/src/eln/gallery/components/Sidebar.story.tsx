import React from "react";
import Sidebar from "./Sidebar";
import { Optional } from "@/util/optional";
import {
  CssBaseline,
  StyledEngineProvider,
  ThemeProvider,
} from "@mui/material";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { DisableDragAndDropByDefault } from "@/hooks/browser/useFileImportDragAndDrop";
import Analytics from "@/components/Analytics";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { BrowserRouter } from "react-router-dom";
import ErrorBoundary from "@/components/ErrorBoundary";
import { dummyId } from "../useGalleryListing";
import Alerts from "@/components/Alerts/Alerts";
import { LandmarksProvider } from "@/components/LandmarksContext";

export function DefaultSidebar(): React.ReactNode {
  return (
    <React.StrictMode>
      <ErrorBoundary>
        <BrowserRouter>
          <StyledEngineProvider injectFirst>
            <CssBaseline />
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Analytics>
                <UiPreferences>
                  <DisableDragAndDropByDefault>
                    <Alerts>
                      <LandmarksProvider>
                        <Sidebar
                          selectedSection="Images"
                          setSelectedSection={() => {}}
                          drawerOpen={true}
                          setDrawerOpen={() => {}}
                          folderId={{ tag: "success", value: dummyId() }}
                          refreshListing={() => Promise.resolve()}
                          id="1"
                        />
                      </LandmarksProvider>
                    </Alerts>
                  </DisableDragAndDropByDefault>
                </UiPreferences>
              </Analytics>
            </ThemeProvider>
          </StyledEngineProvider>
        </BrowserRouter>
      </ErrorBoundary>
    </React.StrictMode>
  );
}
