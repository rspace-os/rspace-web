import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import Clustermarket from "./Clustermarket";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-clustermarket");
  // biome-ignore lint/style/noNonNullAssertion: initial biome migration
  const root = createRoot(domContainer!);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <Analytics>
        <Clustermarket
          {...({
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            clustermarket_web_url: (parent.tinymce.activeEditor as any)?.settings.clustermarket_web_url,
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          } as any)}
        />
      </Analytics>
    </I18nRoot>,
  );
});
