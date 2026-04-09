import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";

const StoichiometryTableLoadingDialog = () => (
  <Dialog
    open={true}
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
        <CircularProgress size={40} />
        <Typography variant="body1" textAlign="center">
          Loading molecule information...
        </Typography>
      </Box>
    </DialogContent>
  </Dialog>
);

export default StoichiometryTableLoadingDialog;