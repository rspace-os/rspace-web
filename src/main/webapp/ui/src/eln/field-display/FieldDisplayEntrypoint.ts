import { createPlainI18n } from "@/modules/common/i18n/plainI18n";
import { createLegacyFieldDisplay, type LegacyFieldDisplay } from "./legacyFieldDisplay";

declare global {
  interface Window {
    /** Form display rules for the legacy ELN document, notebook and public pages. */
    elnFieldDisplay: LegacyFieldDisplay;
  }
}

window.elnFieldDisplay = createLegacyFieldDisplay(createPlainI18n());
// Module scripts run once the page is parsed, before jQuery's ready handlers,
// so server-rendered fields are hidden before the legacy scripts start.
window.elnFieldDisplay.refresh();
