import type { i18n as I18nInstance } from "i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import {
  applyFieldDisplayRules,
  FIELD_SELECTOR,
  type FieldDisplayResult,
  markAnswered,
  shownMandatoryFieldsSatisfied,
  syncStoredValue,
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
  /** Call when legacy code reloads a radio or picklist field's data; then `refresh`. */
  syncStoredValue(fieldId: string | number, value: string | null | undefined): void;
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
  // Mounted empty before any change, so screen readers already watch it when the first reveal is announced.
  const region = doc.createElement("div");
  region.setAttribute("role", "status");
  region.setAttribute("aria-live", "polite");
  region.style.cssText = VISUALLY_HIDDEN;
  doc.body.append(region);
  // A later change wins over an announcement still waiting for the catalog.
  let latestChange = 0;

  function announce(names: ReadonlyArray<string>): void {
    const change = ++latestChange;
    const show = () => {
      if (change !== latestChange) return;
      region.textContent = i18n.t("fieldDisplay.fieldsShown", {
        count: names.length,
        fields: formatList(names, i18n.resolvedLanguage ?? i18n.language),
      });
    };
    // Never show the raw key: wait for the catalog if it has not loaded yet.
    if (i18n.isInitialized && i18n.hasLoadedNamespace("common")) show();
    else
      void i18n.loadNamespaces("common").then(() => {
        if (i18n.hasLoadedNamespace("common")) show();
      });
  }

  function refresh(
    root: ParentNode = doc,
    { announce: announceReveals = false }: { announce?: boolean } = {},
  ): FieldDisplayResult {
    const result = applyFieldDisplayRules(root);
    if (announceReveals && result.revealedNames.length > 0) {
      announce(result.revealedNames);
    } else if (result.anyNewlyHidden) {
      latestChange++;
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
    syncStoredValue(fieldId, value) {
      const element = Array.from(doc.querySelectorAll<HTMLElement>(FIELD_SELECTOR)).find(
        (candidate) => candidate.dataset.fieldId === String(fieldId),
      );
      if (element) syncStoredValue(element, value ?? "");
    },
    shownMandatoryFieldsSatisfied,
  };
}
