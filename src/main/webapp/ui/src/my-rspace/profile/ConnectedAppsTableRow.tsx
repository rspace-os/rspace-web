import { faUnlink } from "@fortawesome/free-solid-svg-icons/faUnlink";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import IconButton from "@mui/material/IconButton";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import TransRichText from "@/modules/common/i18n/TransRichText";
import type { ConnectedOAuthApp } from "@/my-rspace/profile/types";

const ConnectedAppsTableRow = ({
  app,
  onConfirmDisconnectApp,
}: {
  app: ConnectedOAuthApp;
  onConfirmDisconnectApp: (clientId: string) => Promise<void>;
}) => {
  const { t } = useTranslation("common");
  const [open, setOpen] = useState(false);

  return (
    <TableRow hover tabIndex={-1} key={app.clientId}>
      <TableCell align="left">{app.clientName}</TableCell>
      <TableCell align="left" padding="none">
        {app.clientId}
      </TableCell>
      <TableCell align="left">{app.scope}</TableCell>
      <TableCell align="right">
        <Tooltip title={t("profile.oauth.connectedApps.disconnect")} enterDelay={100}>
          <IconButton color="inherit" onClick={() => setOpen(true)} sx={{ width: "42px" }}>
            <FontAwesomeIcon icon={faUnlink} size="xs" />
          </IconButton>
        </Tooltip>
        <ConfirmationDialog
          title={t("profile.oauth.connectedApps.confirmDisconnectTitle")}
          consequences={
            <Typography variant="body1">
              <TransRichText
                i18nKey="common:profile.oauth.connectedApps.confirmDisconnectText"
                values={{ clientName: app.clientName }}
              />
            </Typography>
          }
          variant="warning"
          callback={onConfirmDisconnectApp}
          confirmText={app.appName}
          confirmTextLabel={t("profile.oauth.dialog.confirmAppName")}
          handleCloseDialog={() => setOpen(false)}
          open={open}
        />
      </TableCell>
    </TableRow>
  );
};

export default ConnectedAppsTableRow;
