import React from "react";
import NewNote from "./NewNote";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

export function NewNoteStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <NewNote
          record={makeMockSubSample()}
          onErrorStateChange={(hasError) =>
            console.log("Error state:", hasError)
          }
        />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
