import React from "react";
import { ShareDialog } from "./ShareDialog";
import { ThemeProvider } from "@mui/material/styles";
import Portal from "@mui/material/Portal";
import Alerts from "./Alerts/Alerts";
import { DialogBoundary } from "./DialogBoundary";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/workspace";
import AnalyticsContext from "@/stores/contexts/Analytics";

export function NoPreviousShares() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <Alerts>
          <DialogBoundary>
            <ShareDialog
              open
              onClose={() => {}}
              globalIds={["SD1"]}
              names={["Sample Document 1"]}
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
              open
              onClose={() => {}}
              globalIds={["SD2"]}
              names={["A shared document"]}
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
              open
              onClose={() => {}}
              globalIds={["SD3"]}
              names={["Another shared document"]}
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
              open
              onClose={() => {}}
              globalIds={["SD2", "SD3"]}
              names={["A shared document", "Another shared document"]}
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
              open
              onClose={() => {}}
              globalIds={["SD4"]}
              names={["A shared notebook document"]}
            />
          </DialogBoundary>
        </Alerts>
      </Portal>
    </ThemeProvider>
  );
}

export function SharedWithAControlledOpenState() {
  const [open, setOpen] = React.useState(true);

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <Alerts>
          <button type="button" onClick={() => setOpen(true)}>
            Open share dialog
          </button>
          <button type="button" onClick={() => setOpen(false)}>
            Close share dialog
          </button>
          <DialogBoundary>
            <ShareDialog
              open={open}
              onClose={() => setOpen(false)}
              globalIds={["SD1"]}
              names={["Sample Document 1"]}
            />
          </DialogBoundary>
        </Alerts>
      </Portal>
    </ThemeProvider>
  );
}

export function SharedWithAnalyticsCapture() {
  const [open, setOpen] = React.useState(true);

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <Alerts>
          <AnalyticsContext.Provider
            value={{
              isAvailable: true,
              trackEvent: (event) => {
                const w = window as Window & { __trackedEvents?: string[] };
                w.__trackedEvents = w.__trackedEvents ?? [];
                w.__trackedEvents.push(event);
              },
            }}
          >
            <DialogBoundary>
              <ShareDialog
                open={open}
                onClose={() => setOpen(false)}
                globalIds={["SD1"]}
                names={["Sample Document 1"]}
              />
            </DialogBoundary>
          </AnalyticsContext.Provider>
        </Alerts>
      </Portal>
    </ThemeProvider>
  );
}
