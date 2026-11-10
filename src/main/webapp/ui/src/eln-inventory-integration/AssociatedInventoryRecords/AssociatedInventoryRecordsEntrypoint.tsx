import { createRoot } from "react-dom/client";
import Analytics from "@/components/Analytics";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import AssociatedInventoryRecords from "@/eln-inventory-integration/AssociatedInventoryRecords/index";
import I18nRoot from "@/modules/common/i18n/I18nRoot";

const wrapperDiv = document.getElementById("inventoryRecordList");
if (wrapperDiv) {
  const root = createRoot(wrapperDiv);
  root.render(
    <I18nRoot namespaces={["inventory", "common"]}>
      <Analytics>
        <MuiCssLayerProvider>
          <AssociatedInventoryRecords elnDocumentId={parseInt(wrapperDiv.dataset.documentid || "0", 10)} />
        </MuiCssLayerProvider>
      </Analytics>
    </I18nRoot>,
  );
}
