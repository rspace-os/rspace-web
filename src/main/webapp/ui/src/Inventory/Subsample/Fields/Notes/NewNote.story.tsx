import React from "react";
import NewNote from "./NewNote";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import Alerts from "@/Inventory/components/Alerts";

export function NewNoteStory({
  onErrorStateChange,
  createNote,
  isEditable = true,
  setDirtyFlag,
  unsetDirtyFlag,
  state,
}: {
  onErrorStateChange?: (hasError: boolean) => void;
  createNote?: () => Promise<void>;
  isEditable?: boolean;
  setDirtyFlag?: () => void;
  unsetDirtyFlag?: () => void;
  state?: "preview" | "editing";
}): React.ReactNode {
  const mockSubSample = {
    state: state ?? ("preview" as const),
    createNote: createNote ?? (async () => Promise.resolve()),
    isFieldEditable: (field: string) => {
      if (field === "notes") return isEditable;
      return true;
    },
    setDirtyFlag: setDirtyFlag ?? (() => {}),
    unsetDirtyFlag: unsetDirtyFlag ?? (() => {}),
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <NewNote
            record={mockSubSample as any}
            onErrorStateChange={
              onErrorStateChange ?? ((hasError: boolean) => {})
            }
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
