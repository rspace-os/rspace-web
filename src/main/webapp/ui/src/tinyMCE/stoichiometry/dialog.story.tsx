import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import StoichiometryDialog from "./dialog";
import Alerts from "@/Inventory/components/Alerts";

export function StoichiometryDialogWithCalculateButtonStory({
  onTableCreated,
}: {
  onTableCreated?: () => void;
} = {}): React.ReactNode {
  const [open, setOpen] = React.useState(true);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <StoichiometryDialog
            open={open}
            onClose={() => setOpen(false)}
            chemId={12345}
            hasStoichiometryTable={false}
            onTableCreated={onTableCreated}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export function StoichiometryDialogWithTableStory(): React.ReactNode {
  const [open, setOpen] = React.useState(true);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <StoichiometryDialog
            open={open}
            onClose={() => setOpen(false)}
            chemId={12345}
            hasStoichiometryTable={true}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export function StoichiometryDialogClosedStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <div>Dialog is closed</div>
          <StoichiometryDialog
            open={false}
            onClose={() => {}}
            chemId={12345}
            hasStoichiometryTable={false}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
