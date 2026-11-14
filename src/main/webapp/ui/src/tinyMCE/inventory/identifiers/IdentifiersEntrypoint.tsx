import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import IdentifiersDialog, { type Editor } from "@/tinyMCE/inventory/identifiers";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import Alerts from "../../../components/Alerts/Alerts";
import i18n from "../../../modules/common/i18n";
import I18nRoot from "../../../modules/common/i18n/I18nRoot";
import { createPluginDialog } from "../../pluginDialogRoot";

declare const tinyMCE: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
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

type IdentifiersDialogProps = {
  open?: boolean;
  onClose?: () => void;
};

class IdentifiersPlugin {
  constructor(editor: Editor) {
    const dialog = createPluginDialog("tinymce-inventory-identifiers");
    editor.on("remove", () => dialog.release());
    const renderDialog = (props: IdentifiersDialogProps, { claim = false } = {}) => {
      (claim ? dialog.open : dialog.update)(
        <I18nRoot namespaces={["workspace", "common"]}>
          <StyledEngineProvider injectFirst enableCssLayer>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Alerts>
                <IdentifiersDialog editor={editor} open={false} onClose={() => {}} {...props} />
              </Alerts>
            </ThemeProvider>
          </StyledEngineProvider>
        </I18nRoot>,
      );
    };
    const openDialog = () => {
      renderDialog({ open: true, onClose: () => renderDialog({ open: false }) }, { claim: true });
    };

    editor.ui.registry.addMenuItem("optIdentifiers", {
      text: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      icon: "inventory_identifiers",
      onAction: openDialog,
    });
    editor.ui.registry.addButton("identifiers", {
      tooltip: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      icon: "inventory_identifiers",
      onAction: openDialog,
    });
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optIdentifiers", {
      text: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      aliases: ["IGSN"],
      icon: "inventory_identifiers",
      action: openDialog,
    });
  }
}

tinyMCE.PluginManager.add("identifiers", IdentifiersPlugin);
