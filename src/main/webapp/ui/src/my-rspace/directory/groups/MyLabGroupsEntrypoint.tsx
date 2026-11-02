import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import MyLabGroups from "@/my-rspace/directory/groups/MyLabGroups";

const domContainer = document.getElementById("myLabGroups") as HTMLElement;
const root = createRoot(domContainer);
root.render(
  <I18nRoot namespaces={["common"]}>
    <MyLabGroups domContainer={domContainer} />
  </I18nRoot>,
);
