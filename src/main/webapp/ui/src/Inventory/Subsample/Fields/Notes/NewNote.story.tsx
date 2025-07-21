import React from "react";
import NewNote from "./NewNote";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import Alerts from "@/Inventory/components/Alerts";

export function NewNoteStory({
  onErrorStateChange,
  createNote,
}: {
  onErrorStateChange?: (hasError: boolean) => void;
  createNote?: () => Promise<void>;
}): React.ReactNode {
  const mockSubSample = makeMockSubSample();
  mockSubSample.createNote =
    createNote ??
    (async () => {
      return Promise.resolve();
    });
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <NewNote
            record={mockSubSample}
            onErrorStateChange={
              onErrorStateChange ?? ((hasError: boolean) => {})
            }
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
