import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/pyrat";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { createPluginDialog } from "../pluginDialogRoot";
import type { TinyMcePluginEditor } from "../types";
import PyratDialog, { type PyratDialogProps } from "./PyratDialog";

type PyratDialogUpdates = Partial<Pick<PyratDialogProps, "open" | "onClose">>;

declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: TinyMcePluginEditor) => unknown) => void;
  };
};

class PyratPlugin {
  constructor(editor: TinyMcePluginEditor) {
    const dialog = createPluginDialog("tinymce-pyrat");
    editor.on("remove", () => dialog.release());
    const renderDialog = (props: PyratDialogUpdates, { claim = false } = {}) => {
      (claim ? dialog.open : dialog.update)(
        <I18nRoot namespaces={["apps", "common", "workspace"]}>
          <StyledEngineProvider injectFirst enableCssLayer>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <PyratDialog editor={editor} open={false} onClose={() => {}} {...props} />
            </ThemeProvider>
          </StyledEngineProvider>
        </I18nRoot>,
      );
    };
    const openDialog = () => {
      renderDialog({ open: true, onClose: () => renderDialog({ open: false }) }, { claim: true });
    };

    editor.ui.registry.addButton("pyrat", {
      tooltip: i18n.t("workspace:tinymce.pyrat.linkTooltip"),
      icon: "pyrat",
      onAction: openDialog,
    });

    editor.ui.registry.addMenuItem("optPyrat", {
      text: i18n.t("workspace:tinymce.pyrat.fromPyrat"),
      icon: "pyrat",
      onAction: openDialog,
    });

    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optPyrat", {
      text: i18n.t("workspace:tinymce.pyrat.fromPyrat"),
      icon: "pyrat",
      action: openDialog,
    });
  }
}
tinymce.PluginManager.add("pyrat", PyratPlugin);
