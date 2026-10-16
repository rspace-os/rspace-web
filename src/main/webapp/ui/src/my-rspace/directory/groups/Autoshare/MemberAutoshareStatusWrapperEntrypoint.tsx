import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import MemberAutoshareStatusWrapper from "@/my-rspace/directory/groups/Autoshare/MemberAutoshareStatusWrapper";

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
            groupId={groupId}
            groupDisplayName={groupDisplayName}
            subjectId={subjectId}
            canManageAutoshare={canManageAutoshare}
            isLabGroup={isLabGroup}
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
