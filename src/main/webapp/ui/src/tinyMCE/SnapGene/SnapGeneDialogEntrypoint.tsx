import { createRoot } from "react-dom/client";
import Alerts from "@/components/Alerts/Alerts";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import SnapGeneDialog from "@/tinyMCE/SnapGene/SnapGeneDialog";

declare const $: JQueryStatic;
declare const RS: { dnaFiles: string[] };

type SnapGeneId = string | number;
type SnapGeneClickEvent = JQuery.ClickEvent<Document, undefined, Element, Element>;

$(document).on("click", ".snapGenePanel .previewActionLink", (e: SnapGeneClickEvent) => {
  e.preventDefault();
  const targetId = $(e.target).parent().parent()[0].getAttribute("bioid");

  if (targetId === null) {
    throw new Error("SnapGene preview action is missing its record ID");
  }
  renderDialog(targetId);
});

$(document).on("click", ".imageThumbnail", (e: SnapGeneClickEvent) => {
  if (window.localStorage.getItem("snapgene-available") === "true") {
    const target = $(e.target).parent();
    const extension: unknown = target.data("extension");

    if (typeof extension === "string" && RS.dnaFiles.includes(extension)) {
      const id: unknown = target.data("id");
      if (typeof id !== "string" && typeof id !== "number") {
        throw new Error("SnapGene thumbnail is missing its record ID");
      }
      renderDialog(id);
    }
  }
});

// detect iframe load and render elements
document.addEventListener("open-dna-info", (e) => {
  e.preventDefault();
  renderDialog((e as CustomEvent<SnapGeneId>).detail);
});

function renderDialog(targetId: SnapGeneId) {
  $(document.body).append("<span class='snapgene-dialog'></span>");
  const container = document.querySelector(".snapgene-dialog");
  if (!container) {
    throw new Error("SnapGene dialog container not found");
  }
  const root = createRoot(container);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <MuiCssLayerProvider>
        <Alerts>
          <SnapGeneDialog id={targetId} />
        </Alerts>
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
}
