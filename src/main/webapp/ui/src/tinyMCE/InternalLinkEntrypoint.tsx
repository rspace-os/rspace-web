import { ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import InternalLink, { type RemovableElement } from "@/tinyMCE/InternalLink";
import type { RevisionIdentifier } from "@/tinyMCE/types";
import materialTheme from "../theme";

type InsertRevisionDetail = {
  id: RevisionIdentifier;
  version?: RevisionIdentifier | null;
  el?: RemovableElement | ArrayLike<RemovableElement | undefined> | null;
};
type InsertRevisionEvent = CustomEvent<InsertRevisionDetail>;
function isRemovableElement(value: unknown): value is RemovableElement {
  return typeof value === "object" && value !== null && "remove" in value && typeof value.remove === "function";
}
function normalizeInitialElement(initialEl: InsertRevisionDetail["el"]): RemovableElement | null {
  if (!initialEl) {
    return null;
  }
  if (typeof initialEl === "object" && "0" in initialEl) {
    const firstElement = initialEl[0];
    if (isRemovableElement(firstElement)) {
      return firstElement;
    }
  }
  if (isRemovableElement(initialEl)) {
    return initialEl;
  }
  return null;
}

document.addEventListener("tinymce-insert-revision", (event: Event): void => {
  const detail = (event as InsertRevisionEvent).detail;
  if (!detail) {
    return;
  }
  const container = document.createElement("span");
  container.className = "revision-dialog";
  document.body.appendChild(container);
  const root = createRoot(container);
  // The cache provider and ThemeProvider must wrap the MUI components so their
  // styles use the page nonce and theme.
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <MuiCssLayerProvider>
        <ThemeProvider theme={materialTheme}>
          <InternalLink id={detail.id} version={detail.version} initialEl={normalizeInitialElement(detail.el)} />
        </ThemeProvider>
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
});
