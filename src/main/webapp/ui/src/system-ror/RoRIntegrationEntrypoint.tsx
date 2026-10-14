import { createRoot, type Root } from "react-dom/client";
import LoaderCircular from "@/components/LoadingCircular";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import RoRIntegration from "@/system-ror/RoRIntegration";

type HTMLElementWithRorRoot = HTMLElement & { rorRoot?: Root };

function mountRoRIntegration(event?: Event) {
  event?.preventDefault();

  const mainArea: HTMLElementWithRorRoot | null = document.getElementById("mainArea");

  if (!mainArea) {
    return;
  }

  mainArea.rorRoot?.unmount();

  let domContainer = document.getElementById("rorIntegration");

  if (!domContainer) {
    domContainer = document.createElement("div");
    domContainer.id = "rorIntegration";
  }

  domContainer.style.width = "70%";

  mainArea.replaceChildren(domContainer);

  const root = createRoot(domContainer);
  mainArea.rorRoot = root;
  root.render(
    <I18nRoot namespaces={["system"]} fallback={<LoaderCircular />}>
      <RoRIntegration />
    </I18nRoot>,
  );
}

window.addEventListener("load", () => {
  document.getElementById("rorRegistryLink")?.addEventListener("click", (event) => void mountRoRIntegration(event));
});
