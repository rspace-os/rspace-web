import Portal from "@mui/material/Portal";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/workspace";
import Alerts from "./Alerts/Alerts";
import { DialogBoundary } from "./DialogBoundary";
import { ShareDialog } from "./ShareDialog";

export function NoPreviousShares() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Portal>
                <Alerts>
                    <DialogBoundary>
                        <ShareDialog
                            shareDialogConfig={{
                                open: true,
                                onClose: () => {},
                                globalIds: ["SD1"],
                                names: ["Sample Document 1"],
                            }}
                        />
                    </DialogBoundary>
                </Alerts>
            </Portal>
        </ThemeProvider>
    );
}

export function SharedWithAnotherUser() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Portal>
                <Alerts>
                    <DialogBoundary>
                        <ShareDialog
                            shareDialogConfig={{
                                open: true,
                                onClose: () => {},
                                globalIds: ["SD2"],
                                names: ["A shared document"],
                            }}
                        />
                    </DialogBoundary>
                </Alerts>
            </Portal>
        </ThemeProvider>
    );
}

export function SharedWithAGroup() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Portal>
                <Alerts>
                    <DialogBoundary>
                        <ShareDialog
                            shareDialogConfig={{
                                open: true,
                                onClose: () => {},
                                globalIds: ["SD3"],
                                names: ["Another shared document"],
                            }}
                        />
                    </DialogBoundary>
                </Alerts>
            </Portal>
        </ThemeProvider>
    );
}

export function MultipleDocuments() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Portal>
                <Alerts>
                    <DialogBoundary>
                        <ShareDialog
                            shareDialogConfig={{
                                open: true,
                                onClose: () => {},
                                globalIds: ["SD2", "SD3"],
                                names: ["A shared document", "Another shared document"],
                            }}
                        />
                    </DialogBoundary>
                </Alerts>
            </Portal>
        </ThemeProvider>
    );
}

export function DocumentThatHasBeenSharedIntoANotebook() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <Portal>
                <Alerts>
                    <DialogBoundary>
                        <ShareDialog
                            shareDialogConfig={{
                                open: true,
                                onClose: () => {},
                                globalIds: ["SD4"],
                                names: ["A shared notebook document"],
                            }}
                        />
                    </DialogBoundary>
                </Alerts>
            </Portal>
        </ThemeProvider>
    );
}
