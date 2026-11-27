import { createRoot, type Root } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import Analytics from "../../Inventory/Analytics";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import { FAB_SIZE, MaterialsListing, NewMaterialsListing } from "./MaterialsListing";

const materialsListingRoots = new WeakMap<Element, Root>();

function getMaterialsListingRoot(container: Element): Root {
  const existingRoot = materialsListingRoots.get(container);
  if (existingRoot) {
    return existingRoot;
  }

  const root = createRoot(container);
  materialsListingRoots.set(container, root);
  return root;
}

function initListOfMaterials({
  makeWrapperRelative,
  fabRightPadding,
}: {
  makeWrapperRelative: boolean;
  fabRightPadding: number;
}) {
  const globalWindow = window as Window & {
    canBeEditable?: () => boolean;
    isEditable?: boolean;
  };
  let canEdit: boolean | null = null;

  if (typeof globalWindow.canBeEditable === "function") {
    canEdit = globalWindow.canBeEditable();
  } else if (typeof globalWindow.isEditable === "boolean") {
    canEdit = globalWindow.isEditable;
  }

  Array.from(document.getElementsByClassName("invMaterialsListing")).forEach((wrapperDiv) => {
    const listingWrapper = wrapperDiv as HTMLDivElement;
    const { fieldId, documentId } = listingWrapper.dataset;

    if (!fieldId) return;

    const root = getMaterialsListingRoot(listingWrapper);
    root.render(
      <MuiCssLayerProvider container={listingWrapper.ownerDocument}>
        <I18nRoot namespaces={["inventory", "common"]}>
          <Analytics>
            <MaterialsListing elnFieldId={fieldId} canEdit={canEdit} fabRightPadding={fabRightPadding} />
          </Analytics>
        </I18nRoot>
      </MuiCssLayerProvider>,
    );
    if (makeWrapperRelative) listingWrapper.style.position = "relative";

    if (!documentId) return;

    const newButtonWrapper = document.querySelector(
      `.invMaterialsListing_new[data-field-id="${fieldId}"][data-document-id="${documentId}"]`,
    );
    if (newButtonWrapper) {
      getMaterialsListingRoot(newButtonWrapper).render(
        <MuiCssLayerProvider container={newButtonWrapper.ownerDocument}>
          <I18nRoot namespaces={["inventory", "common"]}>
            <Analytics>
              <NewMaterialsListing elnFieldId={fieldId} />
            </Analytics>
          </I18nRoot>
        </MuiCssLayerProvider>,
      );
    }
  });
}

/*
 * This is invoked on page load, for structuredDocument
 */
initListOfMaterials({
  makeWrapperRelative: false,
  fabRightPadding: -FAB_SIZE / 2,
});

/*
 * This is invoked by journal.js, when viewing via notebookEditor
 */
window.addEventListener("listOfMaterialInit", () => {
  initListOfMaterials({
    makeWrapperRelative: true,
    fabRightPadding: -FAB_SIZE,
  });
});
