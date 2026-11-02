import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GroupEditBar from "@/my-rspace/directory/groups/GroupEditBar";

const domContainer = document.getElementById("groupEditBar") as HTMLElement;
const root = createRoot(domContainer);
root.render(
  <I18nRoot namespaces={["common"]}>
    <GroupEditBar domContainer={domContainer} />
  </I18nRoot>,
);
