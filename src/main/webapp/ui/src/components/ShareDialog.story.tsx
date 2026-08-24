import Portal from "@mui/material/Portal";
import { ThemeProvider } from "@mui/material/styles";
import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/workspace";
import AnalyticsContext from "@/stores/contexts/Analytics";
import Alerts from "./Alerts/Alerts";
import { ShareDialog } from "./ShareDialog";

export function NoPreviousShares() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <Alerts>
          <ShareDialog open onClose={() => {}} globalIds={["SD1"]} names={["Sample Document 1"]} />
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
          <ShareDialog open onClose={() => {}} globalIds={["SD2"]} names={["A shared document"]} />
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
          <ShareDialog open onClose={() => {}} globalIds={["SD3"]} names={["Another shared document"]} />
        </Alerts>
      </Portal>
    </ThemeProvider>
  );
}

export function SharedSnippetWithAGroup() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <Alerts>
          <ShareDialog open onClose={() => {}} globalIds={["SD3"]} names={["Another shared snippet"]} isSnippet />
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
          <ShareDialog
            open
            onClose={() => {}}
            globalIds={["SD2", "SD3"]}
            names={["A shared document", "Another shared document"]}
          />
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
          <ShareDialog open onClose={() => {}} globalIds={["SD4"]} names={["A shared notebook document"]} />
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
            {"Open share dialog"}
          </button>
          <button type="button" onClick={() => setOpen(false)}>
            {"Close share dialog"}
          </button>
          <ShareDialog open={open} onClose={() => setOpen(false)} globalIds={["SD1"]} names={["Sample Document 1"]} />
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
            <ShareDialog open={open} onClose={() => setOpen(false)} globalIds={["SD1"]} names={["Sample Document 1"]} />
          </AnalyticsContext.Provider>
        </Alerts>
      </Portal>
    </ThemeProvider>
  );
}
