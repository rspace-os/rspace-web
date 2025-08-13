import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import React from "react";
import {ErrorReason} from "./Enums";
import SvgIcon from '@mui/material/SvgIcon';
import Collapse from "@mui/material/Collapse";
import Button from "@mui/material/Button";
import CloseIcon from '@mui/icons-material/Close';
import IconButton from "@mui/material/IconButton";

export default function ErrorView({
                                    errorReason,
                                    errorMessage, WorkFlowIcon
                                  }: {
  errorReason: (typeof ErrorReason)[keyof typeof ErrorReason];
  errorMessage: string;
  WorkFlowIcon: typeof SvgIcon;
}): React.ReactNode {
  const [open, setOpen] = React.useState(true);
  return (
      <>
        <Collapse in={open}>
          <Alert severity="error" icon={<WorkFlowIcon fontSize="inherit"/>}>
            <AlertTitle>Error</AlertTitle>
            <IconButton
                aria-label="close"
                color="inherit"
                size="large"
                onClick={() => {
                  setOpen(false);
                }}
            >
              <CloseIcon fontSize="inherit"/>
            </IconButton>
            {/* When Galaxy API KEY is invalid Galaxy API responds with 403 */}
            {errorReason === ErrorReason.Unauthorized && (
                <>Invalid Galaxy API Key Please re-enter your API Key on the Apps page.</>
            )}
            {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
            {errorReason === ErrorReason.NotFound && (
                <>Unable to retrieve any relevant results. Error message was: {errorMessage}</>
            )}

            {errorReason === ErrorReason.UNKNOWN && (
                <>Unknown issue, please investigate whether your Galaxy Server(s) is/are running.
                  Error message was: {errorMessage}</>
            )}
          </Alert>
        </Collapse>
        <Button
            disabled={open}
            variant="outlined"
            onClick={() => {
              setOpen(true);
            }}
        >
          See Galaxy error
        </Button>
      </>
  );
}
