import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";

function StoichiometryTableLoadingDialog({
  open,
  message = "Loading molecule information...",
}: {
  open: boolean;
  message?: string;
}): React.ReactNode {
  return (
    <Dialog
      open={open}
      disableEscapeKeyDown
      sx={{
        "& .MuiDialog-paper": {
          minWidth: 300,
        },
      }}
    >
      <DialogContent>
        <Box
          display="flex"
          flexDirection="column"
          justifyContent="center"
          alignItems="center"
          py={3}
          gap={2}
        >
          <CircularProgress size={40} aria-label={message} />
          <Typography variant="body1" textAlign="center">
            {message}
          </Typography>
        </Box>
      </DialogContent>
    </Dialog>
  );
}

export default StoichiometryTableLoadingDialog;