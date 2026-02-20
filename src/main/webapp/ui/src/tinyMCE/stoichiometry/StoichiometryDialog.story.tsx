import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import StoichiometryDialog from "./StoichiometryDialog";
import Alerts from "../../components/Alerts/Alerts";

export function StoichiometryDialogWithCalculateButtonStory({
  onTableCreated,
}: {
  onTableCreated?: () => void;
} = {}): React.ReactNode {
  const [open, setOpen] = React.useState(true);
  const [stoichiometryId, setStoichiometryId] = React.useState<
    number | undefined
  >(undefined);
  const [stoichiometryRevision, setStoichiometryRevision] = React.useState<
    number | undefined
  >(undefined);

  const handleTableCreated = () => {
    setStoichiometryId(1);
    setStoichiometryRevision(1);
    onTableCreated?.();
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <StoichiometryDialog
            open={open}
            onClose={() => setOpen(false)}
            chemId={12345}
            recordId={1}
            stoichiometryId={stoichiometryId}
            stoichiometryRevision={stoichiometryRevision}
            onTableCreated={handleTableCreated}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
export function StoichiometryDialogWithTableStory({
  onChangesUpdate,
  onSave,
  onDelete,
}: {
  onChangesUpdate?: (hasChanges: boolean) => void;
  onSave?: () => void;
  onDelete?: () => void;
} = {}): React.ReactNode {
  const [open, setOpen] = React.useState(true);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <StoichiometryDialog
            open={open}
            onClose={() => setOpen(false)}
            chemId={12345}
            recordId={1}
            stoichiometryId={1}
            stoichiometryRevision={1}
            onSave={onSave}
            onDelete={onDelete}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export function StoichiometryDialogClosedStory(): React.ReactNode {
  const [stoichiometryId, setStoichiometryId] = React.useState<
    number | undefined
  >(undefined);
  const [stoichiometryRevision, setStoichiometryRevision] = React.useState<
    number | undefined
  >(undefined);

  const handleTableCreated = () => {
    setStoichiometryId(1);
    setStoichiometryRevision(1);
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <div>Dialog is closed</div>
          <StoichiometryDialog
            open={false}
            onClose={() => {}}
            chemId={12345}
            recordId={1}
            stoichiometryId={stoichiometryId}
            stoichiometryRevision={stoichiometryRevision}
            onTableCreated={handleTableCreated}
          />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
