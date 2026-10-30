import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import AutoshareStatus from "../../../profile/Autoshare/AutoshareStatus";

export type AutoshareMemberStatus = {
  userId: number;
  username: string;
  isPI: boolean;
  autoshareEnabled: boolean;
  isAutoshareInProgress: boolean;
};

type MemberAutoshareStatusWrapperProps = AutoshareMemberStatus & {
  isCloud: boolean;
  isGroupAutoshareAllowed: boolean;
  groupId: string | undefined;
  groupDisplayName: string | undefined;
  subjectId: number;
  canManageAutoshare: boolean;
  isLabGroup: boolean;
};

function MemberAutoshareStatusWrapper(props: MemberAutoshareStatusWrapperProps) {
  const { t } = useTranslation("common");
  const [isCurrentlySharing, setIsCurrentlySharing] = React.useState(props.isAutoshareInProgress);
  const [autoshareEnabled, setAutoshareEnabled] = React.useState(props.autoshareEnabled);

  const fetchShareStatus = () => {
    const url = `/groups/ajax/autoshareMemberStatus/${props.groupId}/${props.userId}`;

    axios.get<{ data: AutoshareMemberStatus }>(url).then((response) => {
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
    groupId: props.groupId,
    groupDisplayName: props.groupDisplayName,
    labGroup: true,
    autoshareEnabled,
  };

  let isSwitchDisabled = false;
  let switchDisabledReason = "";

  if (props.userId !== props.subjectId) {
    if (props.isCloud) {
      isSwitchDisabled = true;
      switchDisabledReason = t("profile.groups.manager.onlyEnterprise");
    } else if (!props.canManageAutoshare || props.isPI) {
      isSwitchDisabled = true;
    } else if (!props.isGroupAutoshareAllowed) {
      isSwitchDisabled = true;
      switchDisabledReason = t("profile.groups.manager.contactAdmin");
    }
  }

  if (!props.isLabGroup) {
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

export default MemberAutoshareStatusWrapper;
