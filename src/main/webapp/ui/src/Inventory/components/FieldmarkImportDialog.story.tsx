import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import Alerts from "../../components/Alerts/Alerts";
import ConfirmProvider from "../../components/ConfirmProvider";
import materialTheme from "../../theme";
import FieldmarkImportDialog from "./FieldmarkImportDialog";

export function FieldmarkImportDialogStory() {
    return (
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
                <Alerts>
                    <ConfirmProvider>
                        <FieldmarkImportDialog open={true} onClose={() => {}} />
                    </ConfirmProvider>
                </Alerts>
            </ThemeProvider>
        </StyledEngineProvider>
    );
}
