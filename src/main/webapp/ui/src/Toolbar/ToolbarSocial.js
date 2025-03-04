import React, { useEffect } from "react";
import axios from "@/common/axios";
import styled from "@emotion/styled";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import Badge from "@mui/material/Badge";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faBell,
  faEnvelope,
  faPaperPlane,
} from "@fortawesome/free-solid-svg-icons";
import { faSlack } from "@fortawesome/free-brands-svg-icons";
library.add(faBell, faEnvelope, faPaperPlane, faSlack);

const SocialActionsWrapper = styled.div`
  display: flex;
`;

export default function ToolbarSocial(props) {
  const [notificationCount, setNotificationCount] = React.useState(0);
  const [messageCount, setMessageCount] = React.useState(0);

  useEffect(() => {
    axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
    const getNotifications = async () => {
      try {
        const response = await axios.get("/dashboard/ajax/poll");
        setNotificationCount(response.data.data.notificationCount);
        setMessageCount(response.data.data.messageCount);
      } catch (e) {
        console.log(e);
      }
    };
    getNotifications();
    // update notifications and messages every 10s
    setInterval(() => {
      getNotifications();
    }, 10 * 1000);
  }, []);

  return (
    <SocialActionsWrapper style={props.style}>
      <Tooltip title="Notifications" enterDelay={300}>
        <IconButton
          id="openNotificationDlgLink"
          color="inherit"
          data-test-id="toolbar-notifications"
          aria-label="Notifications"
        >
          <Badge badgeContent={notificationCount} color="secondary">
            <FontAwesomeIcon icon="bell" />
          </Badge>
        </IconButton>
      </Tooltip>
      <Tooltip title="Received messages" enterDelay={300}>
        <IconButton
          id="openMessageDlgLink"
          color="inherit"
          data-test-id="toolbar-messages"
          aria-label="Received messages"
        >
          <Badge badgeContent={messageCount} color="secondary">
            <FontAwesomeIcon icon="envelope" />
          </Badge>
        </IconButton>
      </Tooltip>
      <Tooltip title="Send a message" enterDelay={300}>
        <IconButton
          id="createRequest"
          color="inherit"
          data-test-id="toolbar-send-message"
          aria-label="Send a message"
          onClick={props.onCreateRequest ?? (() => {})}
        >
          <FontAwesomeIcon icon="paper-plane" />
        </IconButton>
      </Tooltip>
      {window.hasOwnProperty("SLACK") && SLACK && props.showExternal && (
        <Tooltip title="Send message on Slack" enterDelay={300}>
          <IconButton
            color="inherit"
            data-test-id="toolbar-send-message-slack"
            style={{ backgroundColor: "white" }}
            className="createExtMessage"
            data-app="SLACK"
            aria-label="Send a message on Slack"
          >
            <img src="/images/icons/slack.png" style={{ width: "24px" }} />
          </IconButton>
        </Tooltip>
      )}
      {window.hasOwnProperty("MSTEAMS") && MSTEAMS && props.showExternal && (
        <Tooltip title="Send message on MicrosoftTeams" enterDelay={300}>
          <IconButton
            data-test-id="toolbar-send-message-teams"
            style={{ backgroundColor: "white" }}
            className="createExtMessage"
            data-app="MSTEAMS"
            aria-label="Send message on MicrosoftTeams"
          >
            <img
              src="/images/icons/microsoftteams.png"
              style={{ width: "24px" }}
            />
          </IconButton>
        </Tooltip>
      )}
    </SocialActionsWrapper>
  );
}
