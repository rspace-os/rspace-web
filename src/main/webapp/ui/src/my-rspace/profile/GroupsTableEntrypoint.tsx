import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GroupsTable from "@/my-rspace/profile/GroupsTable";

const domContainer = document.getElementById("labgroups-table");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <GroupsTable
        username={domContainer.dataset.username}
        userId={domContainer.dataset.userid}
        canEdit={domContainer.dataset.canedit === "true"}
      />
    </I18nRoot>,
  );
}
