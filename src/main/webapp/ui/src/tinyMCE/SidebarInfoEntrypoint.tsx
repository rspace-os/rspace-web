import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { createRoot, type Root } from "react-dom/client";
import SidebarInfo from "@/tinyMCE/SidebarInfo";
import I18nRoot from "../modules/common/i18n/I18nRoot";
import materialTheme from "../theme";

const sidebarRoots = new WeakMap<HTMLElement, Root>();

function getCustomEventDetail(event: Event): unknown {
  return "detail" in event ? event.detail : undefined;
}

function findSidebarContainer(iframe: HTMLIFrameElement): HTMLElement | null {
  const editorContainer = iframe.closest(".tox-tinymce");
  const container =
    editorContainer?.querySelector<HTMLElement>(".tox-sidebar__pane-container") ??
    document.querySelector<HTMLElement>(".tox-sidebar__pane-container");

  return container instanceof HTMLElement ? container : null;
}

document.addEventListener("tinymce-iframe-loaded", (event: Event) => {
  const iframeSelector = getCustomEventDetail(event);
  if (typeof iframeSelector !== "string") {
    return;
  }

  const iframe = document.querySelector(iframeSelector);
  const container = iframe instanceof HTMLIFrameElement ? findSidebarContainer(iframe) : null;

  if (!(iframe instanceof HTMLIFrameElement) || !(container instanceof HTMLElement)) {
    return;
  }

  const root = sidebarRoots.get(container) ?? createRoot(container);
  sidebarRoots.set(container, root);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <SidebarInfo iframe={iframe} />
        </ThemeProvider>
      </StyledEngineProvider>
    </I18nRoot>,
  );
});
