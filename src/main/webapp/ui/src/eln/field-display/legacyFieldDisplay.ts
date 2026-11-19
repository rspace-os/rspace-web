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
   * With `announce`, fields that are newly shown or hidden are announced
   * politely; pass it only after a user's change, never on load or after a
   * server refresh.
   */
  refresh(root?: ParentNode, options?: { announce?: boolean }): FieldDisplayResult;
  /** Call from a field control's change handler. Focus stays on the control. */
  fieldChanged(control: Element): FieldDisplayResult;
  /** Call when legacy code reloads a radio, picklist or choice field's data; then `refresh`. */
  syncStoredValue(fieldId: string | number, value: string | null | undefined): void;
  shownMandatoryFieldsSatisfied: typeof shownMandatoryFieldsSatisfied;
};

const SHOWN = "fieldDisplay.fieldsShown";
const HIDDEN = "fieldDisplay.fieldsHidden";

const VISUALLY_HIDDEN =
  "position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0";

/**
 * The API the legacy ELN document pages use, with a polite live region like
 * the Inventory record forms' `RevealedFieldsAnnouncer`: it names the fields
 * a change showed or hid.
 */
export function createLegacyFieldDisplay(i18n: I18nInstance, doc: Document = document): LegacyFieldDisplay {
  // Mounted empty before any change, so screen readers already watch it when the first change is announced.
  const region = doc.createElement("div");
  region.setAttribute("role", "status");
  region.setAttribute("aria-live", "polite");
  region.style.cssText = VISUALLY_HIDDEN;
  doc.body.append(region);
  // A later change wins over an announcement still waiting for the catalog.
  let latestChange = 0;

  function announce(shown: ReadonlyArray<string>, hidden: ReadonlyArray<string>): void {
    const change = ++latestChange;
    const parts = (
      [
        { key: SHOWN, names: shown },
        { key: HIDDEN, names: hidden },
      ] as const
    ).filter(({ names }) => names.length > 0);
    // Never show a raw key: say only the messages the catalog has, so a catalog cached from before a
    // message was added still announces the rest, and nothing if the catalog failed to load (a
    // failed load can still count as loaded).
    const available = () => (i18n.isInitialized ? parts.filter(({ key }) => i18n.exists(key, { ns: "common" })) : []);
    const show = (said: typeof parts) => {
      if (change !== latestChange || said.length === 0) return;
      region.textContent = said
        .map(({ key, names }) =>
          i18n.t(key, { count: names.length, fields: formatList(names, i18n.resolvedLanguage ?? i18n.language) }),
        )
        .join(" ");
    };
    if (available().length === parts.length) show(parts);
    else void i18n.loadNamespaces("common").then(() => show(available()));
  }

  function refresh(
    root: ParentNode = doc,
    { announce: announceChanges = false }: { announce?: boolean } = {},
  ): FieldDisplayResult {
    const result = applyFieldDisplayRules(root);
    if (result.revealedNames.length === 0 && result.concealedNames.length === 0) return result;
    if (announceChanges) {
      announce(result.revealedNames, result.concealedNames);
    } else {
      // a change the user did not make is not announced, and an older message no longer applies
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
