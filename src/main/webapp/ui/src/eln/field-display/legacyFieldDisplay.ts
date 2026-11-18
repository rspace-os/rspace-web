import type { i18n as I18nInstance } from "i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import {
  applyFieldDisplayRules,
  type FieldDisplayResult,
  markAnswered,
  shownMandatoryFieldsSatisfied,
} from "./documentFieldDisplay";

export type LegacyFieldDisplay = {
  /**
   * Applies the rules to the fields under `root` (the whole page by default).
   * With `announce`, fields that are newly shown are announced politely; pass
   * it only after a user's change, never on load or after a server refresh.
   */
  refresh(root?: ParentNode, options?: { announce?: boolean }): FieldDisplayResult;
  /** Call from a field control's change handler. Focus stays on the control. */
  fieldChanged(control: Element): FieldDisplayResult;
  shownMandatoryFieldsSatisfied: typeof shownMandatoryFieldsSatisfied;
};

const VISUALLY_HIDDEN =
  "position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0";

/**
 * The API the legacy ELN document pages use, with a polite live region like
 * the Inventory record forms' `RevealedFieldsAnnouncer`: it names the fields
 * a change revealed, and is cleared when a field is hidden so a field shown
 * again is announced again.
 */
export function createLegacyFieldDisplay(i18n: I18nInstance, doc: Document = document): LegacyFieldDisplay {
  let region: HTMLElement | null = null;

  function liveRegion(): HTMLElement {
    if (region === null || !region.isConnected) {
      region = doc.createElement("div");
      region.setAttribute("role", "status");
      region.setAttribute("aria-live", "polite");
      region.style.cssText = VISUALLY_HIDDEN;
      doc.body.append(region);
    }
    return region;
  }

  function refresh(root: ParentNode = doc, { announce = false }: { announce?: boolean } = {}): FieldDisplayResult {
    const result = applyFieldDisplayRules(root);
    if (announce && result.revealedNames.length > 0) {
      liveRegion().textContent = i18n.t("fieldDisplay.fieldsShown", {
        count: result.revealedNames.length,
        fields: formatList(result.revealedNames, i18n.resolvedLanguage ?? i18n.language),
      });
    } else if (result.anyNewlyHidden && region !== null) {
      region.textContent = "";
    }
    return result;
  }

  return {
    refresh,
    fieldChanged(control) {
      markAnswered(control);
      return refresh(doc, { announce: true });
    },
    shownMandatoryFieldsSatisfied,
  };
}
