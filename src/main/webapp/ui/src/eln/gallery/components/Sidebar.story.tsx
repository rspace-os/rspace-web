import { CssBaseline, StyledEngineProvider, ThemeProvider } from "@mui/material";
import React from "react";
import { BrowserRouter } from "react-router-dom";
import createAccentedTheme from "@/accentedTheme";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { DisableDragAndDropByDefault } from "@/hooks/ui/useFileImportDragAndDrop";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import { dummyId } from "../useGalleryListing";
import Sidebar from "./Sidebar";

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
