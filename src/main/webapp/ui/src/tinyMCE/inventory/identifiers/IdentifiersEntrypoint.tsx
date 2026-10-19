import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { createRoot } from "react-dom/client";
import IdentifiersDialog, { type Editor } from "@/tinyMCE/inventory/identifiers";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import Alerts from "../../../components/Alerts/Alerts";
import i18n from "../../../modules/common/i18n";
import I18nRoot from "../../../modules/common/i18n/I18nRoot";
import { Optional } from "../../../util/optional";

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
    function* renderIdentifiers(
      domContainer: HTMLElement,
    ): Generator<IdentifiersDialogProps, void, IdentifiersDialogProps> {
      const root = createRoot(domContainer);
      let newProps: IdentifiersDialogProps = {
        open: false,
        onClose: () => {},
      };
      while (true) {
        newProps = yield newProps;
        root.render(
          <I18nRoot namespaces={["workspace", "common"]}>
            <StyledEngineProvider injectFirst enableCssLayer>
              <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
                <Alerts>
                  <IdentifiersDialog editor={editor} open={false} onClose={() => {}} {...newProps} />
                </Alerts>
              </ThemeProvider>
            </StyledEngineProvider>
          </I18nRoot>,
        );
      }
    }

    const element = Optional.fromNullable(document.getElementById("tinymce-inventory-identifiers")).orElseGet(() => {
      const div = document.createElement("div");
      div.id = "tinymce-inventory-identifiers";
      document.body?.appendChild(div);
      return div;
    });
    const identifiersRenderer = renderIdentifiers(element);
    identifiersRenderer.next({ open: false });

    editor.ui.registry.addMenuItem("optIdentifiers", {
      text: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      icon: "inventory_identifiers",
      onAction: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
    editor.ui.registry.addButton("identifiers", {
      tooltip: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      icon: "inventory_identifiers",
      onAction: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optIdentifiers", {
      text: i18n.t("workspace:tinymce.identifiers.pageTitle"),
      aliases: ["IGSN"],
      icon: "inventory_identifiers",
      action: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
  }
}

tinyMCE.PluginManager.add("identifiers", IdentifiersPlugin);
