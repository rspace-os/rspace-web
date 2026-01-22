import { useState } from "react";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTrashAlt } from "@fortawesome/free-regular-svg-icons/faTrashAlt";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { OAuthApp } from "@/my-rspace/profile/types";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

const OAuthTableRow = ({ app, onDeleteApp }: { app: OAuthApp, onDeleteApp: (clientId: string) => Promise<void>; }) => {
  const [open, setOpen] = useState(false);

  return (
    <TableRow hover tabIndex={-1} key={app.clientId}>
      <TableCell align="left">{app.appName}</TableCell>
      <TableCell align="left" padding="none">
        {app.clientId}
      </TableCell>
      <TableCell align="right">
        <Box
          sx={{
            display: "flex",
            justifyContent: "flex-end",
          }}
        >
          <Tooltip title="Delete" enterDelay={100}>
            <IconButton
              color="inherit"
              onClick={() => setOpen(true)}
              style={{ width: "42px" }}
            >
              <FontAwesomeIcon icon={faTrashAlt} size="xs" />
            </IconButton>
          </Tooltip>
          <ConfirmationDialog
            title="Confirm Deletion"
            consequences={
              <Typography variant="body1">
                Are you sure you want to delete <strong>{app.appName}</strong>?
                All access and refresh tokens will be revoked.
              </Typography>
            }
            variant="warning"
            callback={onDeleteApp}
            confirmText={app.appName}
            confirmTextLabel="Type OAuth app name to confirm"
            handleCloseDialog={() => setOpen(false)}
            open={open}
          />
        </Box>
      </TableCell>
    </TableRow>
  );
};

export default OAuthTableRow;