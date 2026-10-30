import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GroupActivity from "@/my-rspace/profile/GroupActivity";

const domContainer = document.getElementById("group-activity");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <GroupActivity userId={domContainer.dataset.userid} />
    </I18nRoot>,
  );
}
