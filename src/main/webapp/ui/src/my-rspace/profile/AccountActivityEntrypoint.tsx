import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import AccountActivity from "@/my-rspace/profile/AccountActivity";

const domContainer = document.getElementById("account-activity");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <AccountActivity userId={domContainer.dataset.userid} />
    </I18nRoot>,
  );
}
