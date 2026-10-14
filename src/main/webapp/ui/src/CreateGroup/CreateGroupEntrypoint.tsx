import { createRoot } from "react-dom/client";
import CreateGroup from "@/CreateGroup/CreateGroup";
import LoaderCircular from "@/components/LoadingCircular";
import I18nRoot from "@/modules/common/i18n/I18nRoot";

const domContainer = document.getElementById("createGroup");
const selfService = $("#selfServiceLabGroup").length !== 0;
const projectGroup = $("#projectGroup").length !== 0;
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["groups"]} fallback={<LoaderCircular />}>
    <CreateGroup
      currentUser={domContainer?.getAttribute("data-currentUser") ?? null}
      selfService={selfService}
      projectGroup={projectGroup}
    />
  </I18nRoot>,
);
