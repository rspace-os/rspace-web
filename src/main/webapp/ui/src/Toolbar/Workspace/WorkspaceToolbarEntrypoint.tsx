import Skeleton from "@mui/material/Skeleton";
import { createRoot, type Root } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import type { ToolbarUpdate } from "../types";
import WorkspaceToolbar, { type WorkspaceToolbarProps } from "./Toolbar";

let rootNode: Root | null = null;
let domContainer: HTMLElement | null = null;

/*
 * We remember the last props so that renderToolbar only needs to be passed the
 * props that have changed
 */
let prevProps: Omit<WorkspaceToolbarProps, "domContainer"> = {
  conditionalRender: {},
  eventHandlers: {
    onCreateRequest: () => {},
  },
};

window.renderToolbar = (newProps?: ToolbarUpdate) => {
  if (!rootNode || !domContainer) {
    const container = document.getElementById("toolbar2");
    if (!container) {
      throw new Error("Required workspace toolbar mount container #toolbar2 was not found.");
    }
    domContainer = container;
    rootNode = createRoot(container);
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
  };
  rootNode.render(
    // content() uses i18n.t() directly, not the hook, so I18nRoot must gate this whole render or labels freeze as raw keys.
    <I18nRoot namespaces={["workspace", "common"]} fallback={<Skeleton variant="rectangular" height={64} />}>
      <WorkspaceToolbar domContainer={domContainer} {...prevProps} />
    </I18nRoot>,
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched by the toolbar.
 */
window.addEventListener("load", () => {
  window.renderToolbar();
});
