import { useState } from "react";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faUnlink } from "@fortawesome/free-solid-svg-icons/faUnlink";
import { ConnectedOAuthApp } from "@/my-rspace/profile/types";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import Typography from "@mui/material/Typography";

const ConnectedAppsTableRow = ({
  app,
  onConfirmDisconnectApp,
}: {
  app: ConnectedOAuthApp;
  onConfirmDisconnectApp: (clientId: string) => Promise<void>;
}) => {
  const [open, setOpen] = useState(false);

  return (
    <TableRow hover tabIndex={-1} key={app.clientId}>
      <TableCell align="left">{app.clientName}</TableCell>
      <TableCell align="left" padding="none">
        {app.clientId}
      </TableCell>
      <TableCell align="left">{app.scope}</TableCell>
      <TableCell align="right">
        <Tooltip title="Disconnect" enterDelay={100}>
          <IconButton
            color="inherit"
            onClick={() => setOpen(true)}
            style={{ width: "42px" }}
          >
            <FontAwesomeIcon icon={faUnlink} size="xs" />
          </IconButton>
        </Tooltip>
        <ConfirmationDialog
          title="Confirm Disconnect"
          consequences={
            <Typography variant="body1">
              Are you sure you want to revoke access from{" "}
              <strong>${app.clientName}</strong>?
            </Typography>
          }
          variant="warning"
          callback={onConfirmDisconnectApp}
          confirmText={app.appName}
          confirmTextLabel="Type OAuth app name to confirm"
          handleCloseDialog={() => setOpen(false)}
          open={open}
        />
      </TableCell>
    </TableRow>
  );
};

export default ConnectedAppsTableRow;
