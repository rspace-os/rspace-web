import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import Omero from "./Omero";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-omero");
  // biome-ignore lint/style/noNonNullAssertion: initial biome migration
  const root = createRoot(domContainer!);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      {/* biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
      <Omero omero_web_url={(parent.tinymce.activeEditor as any)?.settings.omero_web_url} />
    </I18nRoot>,
  );
});
