import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/pyrat";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import PyratDialog from "./PyratDialog";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const tinymce: any;

class PyratPlugin {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(editor: any) {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    function* renderPyrat(domContainer: any): Generator<void, void, any> {
      const root = createRoot(domContainer);
      while (true) {
        const newProps = yield;
        root.render(
          <I18nRoot namespaces={["apps", "common", "workspace"]}>
            <StyledEngineProvider injectFirst enableCssLayer>
              <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
                <PyratDialog editor={editor} open={false} onClose={() => {}} {...newProps} />
              </ThemeProvider>
            </StyledEngineProvider>
          </I18nRoot>,
        );
      }
    }

    if (!document.getElementById("tinymce-pyrat")) {
      const div = document.createElement("div");
      div.id = "tinymce-pyrat";
      document.body.appendChild(div);
    }
    const pyratRenderer = renderPyrat(document.getElementById("tinymce-pyrat"));
    pyratRenderer.next({ open: false });

    // Add a button to the toolbar
    editor.ui.registry.addButton("pyrat", {
      tooltip: i18n.t("workspace:tinymce.pyrat.linkTooltip"),
      icon: "pyrat",
      onAction() {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds a menu item to the insert menu
    editor.ui.registry.addMenuItem("optPyrat", {
      text: i18n.t("workspace:tinymce.pyrat.fromPyrat"),
      icon: "pyrat",
      onAction() {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds an option to the slash-menu
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optPyrat", {
      text: i18n.t("workspace:tinymce.pyrat.fromPyrat"),
      icon: "pyrat",
      action: () => {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });
  }
}
tinymce.PluginManager.add("pyrat", PyratPlugin);
