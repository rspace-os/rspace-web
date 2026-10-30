import { createRoot, type Root } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import PreviewInfo, {
  getStoichiometryReference,
  PreviewInfoFallback,
  type PreviewInfoItem,
  STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE,
} from "@/tinyMCE/PreviewInfo";

const previewInfoRoots = new WeakMap<Element, Root>();

function getPreviewInfoRoot(container: Element): Root {
  const existingRoot = previewInfoRoots.get(container);
  if (existingRoot) {
    return existingRoot;
  }

  const root = createRoot(container);
  previewInfoRoots.set(container, root);
  return root;
}

function getAttributes(domNode: Element | null): PreviewInfoItem {
  const attributes: PreviewInfoItem = {};
  if (domNode) {
    Array.from(domNode.attributes).forEach((attr) => {
      attributes[attr.name] = attr.value;
    });
  }
  return attributes;
}

function getPreviewNodes(detail?: string | number): Array<HTMLElement> {
  const selector = `img.chem, div[${STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE}="true"]`;
  const container =
    typeof detail === "string" || typeof detail === "number" ? document.getElementById(`div_${detail}`) : document;

  if (!container) {
    return [];
  }

  return Array.from(container.querySelectorAll<HTMLElement>(selector));
}

function render(attributes: PreviewInfoItem, element: Element) {
  const root = getPreviewInfoRoot(element);
  const isTableOnly = attributes[STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE] === "true";
  root.render(
    <I18nRoot
      namespaces={["apps", "common", "workspace"]}
      fallback={<PreviewInfoFallback isTableOnly={isTableOnly} height={Number(attributes.height) || 200} />}
    >
      <PreviewInfo item={{ ...attributes }} />
    </I18nRoot>,
  );
}

function renderChemPreview(domContainer: HTMLImageElement) {
  const parent = domContainer.parentElement;
  if (!parent) {
    return;
  }

  const attributes = getAttributes(domContainer);
  parent.querySelectorAll("img.chem").forEach((image) => {
    image.remove();
  });
  const contents = parent.innerHTML;

  const root = getPreviewInfoRoot(parent);
  root.render(
    <I18nRoot
      namespaces={["apps", "common", "workspace"]}
      fallback={<PreviewInfoFallback height={Number(attributes.height) || 200} />}
    >
      <PreviewInfo item={{ ...attributes }} />
    </I18nRoot>,
  );
  parent.insertAdjacentHTML("beforeend", contents);
}

function handleDocumentPlaced(event: Event & { detail?: string | number }) {
  getPreviewNodes(event.detail).forEach((domContainer) => {
    if (domContainer instanceof HTMLImageElement) {
      renderChemPreview(domContainer);
      return;
    }

    const attributes = getAttributes(domContainer);
    if (getStoichiometryReference(attributes["data-stoichiometry-table"])) {
      render(attributes, domContainer);
    }
  });

  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new Event("images-replaced"));
}

function handleChemUpdated(event: Event & { detail?: string | number }) {
  const selector = event.detail ? `#div_${event.detail} img.chem` : "img.chem";

  document.querySelectorAll<HTMLImageElement>(selector).forEach((domContainer) => {
    const span = domContainer.closest("span");
    if (!span) {
      return;
    }

    render(getAttributes(domContainer), span);
  });
}

document.addEventListener("document-placed", handleDocumentPlaced);

document.addEventListener("chem-updated", handleChemUpdated);
