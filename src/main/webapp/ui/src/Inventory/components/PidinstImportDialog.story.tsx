import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import Alerts from "../../components/Alerts/Alerts";
import ConfirmProvider from "../../components/ConfirmProvider";
import materialTheme from "../../theme";
import PidinstImportDialog from "./PidinstImportDialog";

export function PidinstImportDialogStory({
  onImported = () => {},
  onClose = () => {},
}: {
  onImported?: (instrument: { id: number; globalId: string }) => void;
  onClose?: () => void;
}) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <ConfirmProvider>
            <PidinstImportDialog open={true} onClose={onClose} onImported={onImported} />
          </ConfirmProvider>
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
