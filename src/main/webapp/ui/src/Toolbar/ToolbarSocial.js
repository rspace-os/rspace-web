import React, { useEffect, useState } from 'react';
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
import { webSocketService } from '../services/WebSocketService';
import useStores from '../stores/use-stores';
import { observer } from 'mobx-react-lite';

library.add(faBell, faEnvelope, faPaperPlane, faSlack);

const SocialActionsWrapper = styled.div`
  display: flex;
`;

export default function ToolbarSocial(props) {
  const { peopleStore, authStore } = useStores();
  const [notificationCount, setNotificationCount] = useState(0);
  const [messageCount, setMessageCount] = useState(0);

  useEffect(() => {
    void (async () => {
      await authStore.synchronizeWithSessionStorage();
      if (!authStore.isAuthenticated) {
      } else {
        const currentUser = await peopleStore.fetchCurrentUser();
        if (currentUser) {
          webSocketService.connect(currentUser.id.toString());
          const subscriberId = webSocketService.addSubscriber((notification) => {
            setNotificationCount(notification.notificationCount);
            setMessageCount(notification.messageCount);
          });

          return () => {
            webSocketService.removeSubscriber(subscriberId);
            webSocketService.disconnect();
          };
        }
      }
    })();
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
