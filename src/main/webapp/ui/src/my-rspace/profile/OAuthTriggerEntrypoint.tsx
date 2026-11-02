import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import OAuthTrigger from "@/my-rspace/profile/OAuthTrigger";

const domContainer = document.getElementById("oAuthApps");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <OAuthTrigger />
    </I18nRoot>,
  );
}
