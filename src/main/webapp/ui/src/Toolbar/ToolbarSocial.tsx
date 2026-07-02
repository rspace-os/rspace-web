/* global MSTEAMS, SLACK */

import { faBell } from "@fortawesome/free-solid-svg-icons/faBell";
import { faEnvelope } from "@fortawesome/free-solid-svg-icons/faEnvelope";
import { faPaperPlane } from "@fortawesome/free-solid-svg-icons/faPaperPlane";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Badge from "@mui/material/Badge";
import Box, { type BoxProps } from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const SLACK: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const MSTEAMS: any;

/**
 * Toolbar actions for messages and notifications.
 */
type ToolbarSocialProps = {
  onCreateRequest?: () => void;
  showExternal?: boolean;
  sx?: BoxProps["sx"];
};

export default function ToolbarSocial(props: ToolbarSocialProps) {
  const { t } = useTranslation("common");
  const [notificationCount, setNotificationCount] = React.useState(0);
  const [messageCount, setMessageCount] = React.useState(0);

  useEffect(() => {
    const getNotifications = async () => {
      try {
        const response = await axios.get("/dashboard/ajax/poll");
        setNotificationCount(response.data.data.notificationCount);
        setMessageCount(response.data.data.messageCount);
      } catch (e) {
        console.error(e);
      }
    };
    getNotifications();
    // update notifications and messages every 10s
    const intervalId = setInterval(() => {
      getNotifications();
    }, 10 * 1000);

    return () => clearInterval(intervalId);
  }, []);

  return (
    <Box sx={{ display: "flex", ...props.sx }}>
      <Tooltip title={t("toolbar.notifications")} enterDelay={300}>
        <IconButton
          id="openNotificationDlgLink"
          color="inherit"
          data-test-id="toolbar-notifications"
          aria-label={t("toolbar.notifications")}
        >
          <Badge badgeContent={notificationCount} color="secondary">
            <FontAwesomeIcon icon={faBell} />
          </Badge>
        </IconButton>
      </Tooltip>
      <Tooltip title={t("toolbar.receivedMessages")} enterDelay={300}>
        <IconButton
          id="openMessageDlgLink"
          color="inherit"
          data-test-id="toolbar-messages"
          aria-label={t("toolbar.receivedMessages")}
        >
          <Badge badgeContent={messageCount} color="secondary">
            <FontAwesomeIcon icon={faEnvelope} />
          </Badge>
        </IconButton>
      </Tooltip>
      <Tooltip title={t("toolbar.sendMessage")} enterDelay={300}>
        <IconButton
          id="createRequest"
          color="inherit"
          data-test-id="toolbar-send-message"
          aria-label={t("toolbar.sendMessage")}
          onClick={props.onCreateRequest ?? (() => {})}
        >
          <FontAwesomeIcon icon={faPaperPlane} />
        </IconButton>
      </Tooltip>
      {Object.hasOwn(window, "SLACK") && SLACK && props.showExternal && (
        <Tooltip title={t("toolbar.sendMessageOnSlack")} enterDelay={300}>
          <IconButton
            color="inherit"
            data-test-id="toolbar-send-message-slack"
            sx={{ backgroundColor: "white" }}
            className="createExtMessage"
            data-app="SLACK"
            aria-label={t("toolbar.sendMessageOnSlack")}
          >
            <Box component="img" src="/images/icons/slack.png" alt={t("toolbar.slack")} sx={{ width: "24px" }} />
          </IconButton>
        </Tooltip>
      )}
      {Object.hasOwn(window, "MSTEAMS") && MSTEAMS && props.showExternal && (
        <Tooltip title={t("toolbar.sendMessageOnMicrosoftTeams")} enterDelay={300}>
          <IconButton
            data-test-id="toolbar-send-message-teams"
            sx={{ backgroundColor: "white" }}
            className="createExtMessage"
            data-app="MSTEAMS"
            aria-label={t("toolbar.sendMessageOnMicrosoftTeams")}
          >
            <Box
              component="img"
              src="/images/icons/microsoftteams.png"
              alt={t("toolbar.microsoftTeams")}
              sx={{ width: "24px" }}
            />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  );
}
