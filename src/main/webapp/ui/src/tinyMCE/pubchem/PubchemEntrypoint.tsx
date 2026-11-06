import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import type React from "react";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";
import { createPluginDialog } from "../pluginDialogRoot";
import type { TinyMcePluginEditor } from "../types";
import ImportDialog from "./ImportDialog";

declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: TinyMcePluginEditor) => unknown) => void;
  };
  activeEditor: TinyMcePluginEditor;
};

declare global {
  interface Window {
    insertActions?: Map<
      string,
      {
        text: string;
        icon: string;
        action: () => void;
        aliases?: string[];
      }
    >;
  }
}

class PubchemPlugin {
  constructor(editor: TinyMcePluginEditor) {
    const dialog = createPluginDialog("tinymce-pubchem");
    editor.on("remove", () => dialog.release());
    const renderDialog = (
      props: Pick<React.ComponentProps<typeof ImportDialog>, "open" | "onClose">,
      { claim = false } = {},
    ) => {
      (claim ? dialog.open : dialog.update)(
        <I18nRoot namespaces={["common"]}>
          <StyledEngineProvider injectFirst enableCssLayer>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Analytics>
                <ErrorBoundary>
                  <Alerts>
                    <ImportDialog editor={tinymce.activeEditor} {...props} />
                  </Alerts>
                </ErrorBoundary>
              </Analytics>
            </ThemeProvider>
          </StyledEngineProvider>
        </I18nRoot>,
      );
    };
    const closeDialog = () => renderDialog({ open: false, onClose: () => {} });
    const openDialog = () => renderDialog({ open: true, onClose: closeDialog }, { claim: true });

    editor.ui.registry.addButton("pubchem", {
      tooltip: i18n.t("common:stoichiometry.addReagent.sources.pubChem.dialogTitle"),
      icon: "pubchem",
      onAction: openDialog,
    });

    editor.ui.registry.addMenuItem("optPubchem", {
      text: i18n.t("common:stoichiometry.addReagent.sources.pubChem.dialogTitle"),
      icon: "pubchem",
      onAction: openDialog,
    });

    if (!window.insertActions) {
      window.insertActions = new Map();
    }
    window.insertActions.set("optPubchem", {
      text: i18n.t("common:stoichiometry.addReagent.sources.pubChem.title"),
      icon: "pubchem",
      action: openDialog,
    });
  }
}

tinymce.PluginManager.add("pubchem", PubchemPlugin);
