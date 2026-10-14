import { createRoot } from "react-dom/client";
import LoaderCircular from "@/components/LoadingCircular";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import NewLabGroup from "@/system-groups/NewLabGroup";

const domContainer = document.getElementById("newLabGroup");
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["groups"]} fallback={<LoaderCircular />}>
    <NewLabGroup />
  </I18nRoot>,
);
