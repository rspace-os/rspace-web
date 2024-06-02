import React from "react";
import axios from "axios";
import AutoshareStatus from "../../../profile/Autoshare/AutoshareStatus";
import { createRoot } from "react-dom/client";

function MemberAutoshareStatusWrapper(props) {
  const [isCurrentlySharing, setIsCurrentlySharing] = React.useState(
    props.isAutoshareInProgress
  );
  const [autoshareEnabled, setAutoshareEnabled] = React.useState(
    props.autoshareEnabled
  );

  const fetchShareStatus = () => {
    const url = `/groups/ajax/autoshareMemberStatus/${groupId}/${props.userId}`;

    axios.get(url).then((response) => {
      const isAutoshareInProgress = response.data.data.isAutoshareInProgress;
      const isAutoshareEnabled = response.data.data.autoshareEnabled;

      setIsCurrentlySharing(isAutoshareInProgress);
      setAutoshareEnabled(isAutoshareEnabled);

      if (isAutoshareInProgress) {
        setTimeout(() => {
          fetchShareStatus();
        }, 3000);
      }
    });
  };

  const group = {
    groupId: groupId,
    groupDisplayName: groupDisplayName,
    labGroup: true,
    autoshareEnabled: autoshareEnabled,
  };

  let isSwitchDisabled = false;
  let switchDisabledReason = "";

  if (props.userId !== subjectId) {
    if (isCloud) {
      isSwitchDisabled = true;
      switchDisabledReason = "Only available on Enterprise";
    } else if (!canManageAutoshare || props.isPI) {
      isSwitchDisabled = true;
    } else if (!isGroupAutoshareAllowed) {
      isSwitchDisabled = true;
      switchDisabledReason =
        "Please contact your system administrator to enable this feature";
    }
  }

  if (!isLabGroup) {
    return <> n/a </>;
  } else {
    return (
      <AutoshareStatus
        group={group}
        username={props.username}
        userId={props.userId}
        isCurrentlySharing={isCurrentlySharing}
        callback={fetchShareStatus}
        isSwitch={true}
        isSwitchDisabled={isSwitchDisabled}
        switchDisabledReason={switchDisabledReason}
      />
    );
  }
}

const domContainer = document.getElementById("memberAutoshareStatusWrapper");

const isCloud = domContainer.dataset.iscloud === "true";
const groupId = domContainer.dataset.groupid;
const groupDisplayName = domContainer.dataset.displayname;
const subjectId = parseInt(domContainer.dataset.subjectid);
const canManageAutoshare = domContainer.dataset.canmanageautoshare === "true";
const isGroupAutoshareAllowed =
  domContainer.dataset.isgroupautoshareallowed === "true";
const isLabGroup = domContainer.dataset.islabgroup === "true";

const url = `/groups/ajax/autoshareMemberStatus/${groupId}`;

axios.get(url).then((response) => {
  const members = response.data.data;

  members
    .filter((member) =>
      Boolean(document.getElementById(`autoshareStatus-${member.userId}`))
    )
    .forEach((member) => {
      const root = createRoot(
        document.getElementById(`autoshareStatus-${member.userId}`)
      );
      root.render(
        <MemberAutoshareStatusWrapper
          isCloud={isCloud}
          isGroupAutoshareAllowed={isGroupAutoshareAllowed}
          userId={member.userId}
          username={member.username}
          isPI={member.isPI}
          autoshareEnabled={member.autoshareEnabled}
          isAutoshareInProgress={member.isAutoshareInProgress}
        />
      );
    });
});
