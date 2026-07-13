import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import AutoshareStatus from "../../../profile/Autoshare/AutoshareStatus";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function MemberAutoshareStatusWrapper(props: any) {
  const { t } = useTranslation("common");
  const [isCurrentlySharing, setIsCurrentlySharing] = React.useState(props.isAutoshareInProgress);
  const [autoshareEnabled, setAutoshareEnabled] = React.useState(props.autoshareEnabled);

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
    groupId,
    groupDisplayName,
    labGroup: true,
    autoshareEnabled,
  };

  let isSwitchDisabled = false;
  let switchDisabledReason = "";

  if (props.userId !== subjectId) {
    if (isCloud) {
      isSwitchDisabled = true;
      switchDisabledReason = t("profile.groups.manager.onlyEnterprise");
    } else if (!canManageAutoshare || props.isPI) {
      isSwitchDisabled = true;
    } else if (!isGroupAutoshareAllowed) {
      isSwitchDisabled = true;
      switchDisabledReason = t("profile.groups.manager.contactAdmin");
    }
  }

  if (!isLabGroup) {
    return <>{t("profile.groups.autosharing.memberStatus.notApplicable")}</>;
  }
  return (
    <Alerts>
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
    </Alerts>
  );
}

const domContainer = document.getElementById("memberAutoshareStatusWrapper");

const isCloud = domContainer?.dataset.iscloud === "true";
const groupId = domContainer?.dataset.groupid;
const groupDisplayName = domContainer?.dataset.displayname;
const subjectId = parseInt(domContainer?.dataset.subjectid ?? "", 10);
const canManageAutoshare = domContainer?.dataset.canmanageautoshare === "true";
const isGroupAutoshareAllowed = domContainer?.dataset.isgroupautoshareallowed === "true";
const isLabGroup = domContainer?.dataset.islabgroup === "true";

const url = `/groups/ajax/autoshareMemberStatus/${groupId}`;

axios.get(url).then((response) => {
  const members = response.data.data;

  members
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    .filter((member: any) => Boolean(document.getElementById(`autoshareStatus-${member.userId}`)))
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    .forEach((member: any) => {
      const root = createRoot(document.getElementById(`autoshareStatus-${member.userId}`) as HTMLElement);
      root.render(
        <I18nRoot namespaces={["common"]}>
          <MemberAutoshareStatusWrapper
            isCloud={isCloud}
            isGroupAutoshareAllowed={isGroupAutoshareAllowed}
            userId={member.userId}
            username={member.username}
            isPI={member.isPI}
            autoshareEnabled={member.autoshareEnabled}
            isAutoshareInProgress={member.isAutoshareInProgress}
          />
        </I18nRoot>,
      );
    });
});
