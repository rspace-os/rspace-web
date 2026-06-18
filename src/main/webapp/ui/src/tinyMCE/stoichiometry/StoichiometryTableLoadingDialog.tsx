import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog, { dialogClasses } from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import Typography from "@mui/material/Typography";

const StoichiometryTableLoadingDialog = () => (
  <Dialog
    open={true}
    sx={{
      [`& .${dialogClasses.paper}`]: {
        minWidth: 300,
      },
    }}
  >
    <DialogContent>
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          py: 3,
          gap: 2,
        }}
      >
        <CircularProgress size={40} />
        <Typography variant="body1" sx={{ textAlign: "center" }}>
          Loading molecule information...
        </Typography>
      </Box>
    </DialogContent>
  </Dialog>
);

export default StoichiometryTableLoadingDialog;
