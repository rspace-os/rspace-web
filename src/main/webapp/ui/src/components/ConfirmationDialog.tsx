import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { type FormEventHandler, useId } from "react";
import { createRoot } from "react-dom/client";
import materialTheme from "../theme";

declare global {
    interface RSGlobal {
        createConfirmationDialog?: (payload: ConfirmActionPayload) => void;
    }

    interface Window {
        RS: RSGlobal;
    }
}

export interface ConfirmActionPayload {
    title: string;
    consequences: string | JSX.Element;
    confirmTextLabel?: string;
    confirmText?: string;
    variant: string;
    // eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
    callback: Function;
}

export function ConfirmationDialog({
    title,
    consequences,
    confirmTextLabel,
    confirmText,
    callback,
    handleCloseDialog,
    open = true,
}: ConfirmActionPayload & {
    handleCloseDialog: () => void;
    open?: boolean;
}) {
    const [input, setInput] = React.useState("");
    const [error, setError] = React.useState<string | null>(null);
    const formTitle = useId();

    const focusInputField = (input: HTMLInputElement) => {
        if (input) {
            setTimeout(() => input.focus(), 100);
        }
    };

    const handleSubmit: FormEventHandler<HTMLFormElement> = (e) => {
        if (e) {
            e.preventDefault();
        }
        if (confirmText) {
            if (input !== confirmText) {
                setError("Input does not match confirmation text");
                return;
            }
        }

        // eslint-disable-next-line @typescript-eslint/no-unsafe-call
        callback();
        handleCloseDialog();
    };

    return (
        <dialog>
            <form onSubmit={handleSubmit}>
                <Dialog open={open} onClose={handleCloseDialog} fullWidth aria-labelledby={formTitle}>
                    <DialogTitle id={formTitle}>{title}</DialogTitle>
                    <DialogContent style={{ overscrollBehavior: "contain" }}>
                        {typeof consequences === "string" ? (
                            <Typography
                                variant="subtitle1"
                                gutterBottom
                                dangerouslySetInnerHTML={{ __html: consequences }}
                            />
                        ) : (
                            consequences
                        )}

                        {confirmText && (
                            <FormControl error={error !== null} fullWidth>
                                <TextField
                                    data-test-id="confirmation-name"
                                    inputRef={focusInputField}
                                    margin="dense"
                                    placeholder={confirmTextLabel}
                                    fullWidth
                                    value={input}
                                    onChange={(e) => {
                                        setInput(e.target.value);
                                        if (error) {
                                            setError(null);
                                        }
                                    }}
                                    inputProps={{ "aria-label": confirmTextLabel }}
                                    variant="standard"
                                />
                                <FormHelperText id="component-error-text">{error}</FormHelperText>
                            </FormControl>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={handleCloseDialog} color="secondary">
                            Cancel
                        </Button>
                        {/* @ts-expect-error Fix types later */}
                        <Button color="primary" onClick={handleSubmit}>
                            Confirm
                        </Button>
                    </DialogActions>
                </Dialog>
            </form>
        </dialog>
    );
}

export function createConfirmationDialog(payload: ConfirmActionPayload) {
    const domContainer = document.createElement("div");
    document.body.appendChild(domContainer);

    const root = createRoot(domContainer);

    const handleCloseDialog = () => {
        root.unmount();
        domContainer.remove();
    };

    root.render(
        <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
                <ConfirmationDialog {...payload} handleCloseDialog={handleCloseDialog} />
            </ThemeProvider>
        </StyledEngineProvider>,
    );
}

window.addEventListener("load", () => {
    window.RS.createConfirmationDialog = createConfirmationDialog;
});
