import Skeleton from "@mui/material/Skeleton";
import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import StructuredDocumentToolbar from "./Toolbar";

declare global {
  interface Window {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    renderToolbar: (newProps?: any) => void;
  }
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let rootNode: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let domContainer: any;

/*
 * We remember the last props so that renderToolbar only needs to be passed the
 * props that have changed
 */
let prevProps = {
  conditionalRender: {},
  eventHandlers: {
    onCreateRequest: () => {},
    onExportDocument: () => {},
    onCanSign: () => {},
  },
  canSign: false,
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
window.renderToolbar = (newProps: any) => {
  if (!rootNode) {
    domContainer = document.getElementById("toolbar2");
    rootNode = createRoot(domContainer);
  }
  prevProps = {
    conditionalRender: {
      ...prevProps.conditionalRender,
      ...(newProps?.conditionalRender ?? {}),
    },
    eventHandlers: {
      ...prevProps.eventHandlers,
      ...(newProps?.eventHandlers ?? {}),
    },
    canSign: newProps?.canSign ?? prevProps.canSign,
  };
  rootNode.render(
    // content() uses i18n.t() directly, not the hook, so I18nRoot must gate this whole render or labels freeze as raw keys.
    <I18nRoot namespaces={["common"]} fallback={<Skeleton variant="rectangular" height={64} />}>
      <StructuredDocumentToolbar domContainer={domContainer} {...prevProps} canSign={prevProps.canSign} />
    </I18nRoot>,
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched in the toolbar's componentDidMount.
 */
window.addEventListener("load", () => {
  const domContainer = document.getElementById("toolbar2");
  window.renderToolbar({
    canSign: domContainer?.getAttribute("data-can-sign") === "true",
  });
});
