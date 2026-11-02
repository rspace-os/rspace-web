import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import ConnectedAppsTrigger from "@/my-rspace/profile/ConnectedAppsTrigger";

const domContainer = document.getElementById("connected-apps");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <ConnectedAppsTrigger />
    </I18nRoot>,
  );
}
