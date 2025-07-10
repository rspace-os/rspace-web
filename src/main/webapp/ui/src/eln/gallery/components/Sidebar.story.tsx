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
import { DisableDragAndDropByDefault } from "@/components/useFileImportDragAndDrop";
import Analytics from "@/components/Analytics";
import { UiPreferences } from "@/util/useUiPreference";
import { BrowserRouter } from "react-router-dom";
import ErrorBoundary from "@/components/ErrorBoundary";
import { dummyId } from "../useGalleryListing";
import Alerts from "@/components/Alerts/Alerts";

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
                      <Sidebar
                        selectedSection="Images"
                        setSelectedSection={() => {}}
                        drawerOpen={true}
                        setDrawerOpen={() => {}}
                        folderId={{ tag: "success", value: dummyId() }}
                        refreshListing={() => Promise.resolve()}
                        id="1"
                      />
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
