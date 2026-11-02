import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GroupActivity from "@/my-rspace/directory/groups/GroupUserActivity";

const domContainer = document.getElementById("groupActivity");
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["common"]}>
    <MuiCssLayerProvider>
      <GroupActivity groupId={domContainer?.dataset.groupid as string} />
    </MuiCssLayerProvider>
  </I18nRoot>,
);
