import { createRoot } from "react-dom/client";
import Alerts from "@/components/Alerts/Alerts";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import SnapGeneDialog from "@/tinyMCE/SnapGene/SnapGeneDialog";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
$(document).on("click", ".snapGenePanel .previewActionLink", (e: any) => {
  e.preventDefault();
  const target_id = $(e.target).parent().parent()[0].getAttribute("bioid");

  renderDialog(target_id);
});

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
$(document).on("click", ".imageThumbnail", (e: any) => {
  if (window.localStorage.getItem("snapgene-available") === "true") {
    const target = $(e.target).parent();

    if (RS.dnaFiles.includes(target.data("extension"))) {
      renderDialog(target.data("id"));
    }
  }
});

// detect iframe load and render elements
document.addEventListener("open-dna-info", (e) => {
  e.preventDefault();
  renderDialog((e as CustomEvent).detail);
});

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function renderDialog(target_id: any) {
  $(document.body).append("<span class='snapgene-dialog'></span>");
  const container = $(".snapgene-dialog")[0];
  const root = createRoot(container);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <MuiCssLayerProvider>
        <Alerts>
          <SnapGeneDialog id={target_id} />
        </Alerts>
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
}
