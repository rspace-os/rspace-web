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
   * Applies the rules to the fields under `root` (the whole page by default)
   * now, from the current values, and cancels an update still waiting for
   * typing to pause. With `announce`, fields that are newly shown or hidden
   * are announced politely; pass it only after a user's change, never on load
   * or after a server refresh. Its `shownById` is what Sign and saves read.
   */
  refresh(root?: ParentNode, options?: { announce?: boolean }): FieldDisplayResult;
  /**
   * Call from a field control's change handler, or a text or number field's
   * input handler. A picked option applies at once; typing applies once it
   * pauses for `TYPING_PAUSE_MS`. Focus stays on the control.
   */
  fieldChanged(control: Element): void;
  /**
   * Call when legacy code puts back a value the user had typed over, which
   * fires no input event. Applies, and announces, once focus has landed, so a
   * field focus moves into stays shown. Sign and saves still call `refresh`.
   */
  valueRestored(): void;
  /**
   * Cancels display work still waiting to run: the update after typing
   * pauses, and an announcement still waiting for its catalog. Call when the
   * user discards their edits.
   */
  cancelPending(): void;
  /** Call when legacy code reloads a field's data; then `refresh`. */
  syncStoredValue(fieldId: string | number, value: string | null | undefined): void;
  shownMandatoryFieldsSatisfied: typeof shownMandatoryFieldsSatisfied;
  /** Removes the live region and the listeners on the page, and cancels pending work. */
  dispose(): void;
};

/** How long typing must pause before the fields it shows or hides change. */
export const TYPING_PAUSE_MS = 300;

const SHOWN = "fieldDisplay.fieldsShown";
const HIDDEN = "fieldDisplay.fieldsHidden";

const VISUALLY_HIDDEN =
  "position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0";

/**
 * The API the legacy ELN document pages use, with a polite live region like
 * the Inventory record forms' `RevealedFieldsAnnouncer`: it names the fields
 * a change showed or hid, once per net change. A field that contains focus is
 * hidden only once focus leaves it.
 */
export function createLegacyFieldDisplay(i18n: I18nInstance, doc: Document = document): LegacyFieldDisplay {
  // Mounted empty before any change, so screen readers already watch it when the first change is announced.
  const region = doc.createElement("div");
  region.setAttribute("role", "status");
  region.setAttribute("aria-live", "polite");
  region.style.cssText = VISUALLY_HIDDEN;
  doc.body.append(region);
  const listeners = new AbortController();
  const { signal } = listeners;
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

  let pending: ReturnType<typeof setTimeout> | undefined;
  // fields kept shown while they contain focus, and whether hiding them is the user's change
  let held: ReadonlyArray<HTMLElement> = [];
  let announceRelease = false;

  function schedule(delay: number): void {
    clearTimeout(pending);
    pending = setTimeout(() => refresh(doc, { announce: true }), delay);
  }

  function clearScheduled(): void {
    clearTimeout(pending);
    pending = undefined;
  }

  function cancelPending(): void {
    clearScheduled();
    latestChange++;
  }

  function release(): void {
    // once focus has landed, so a field it lands in is held in turn
    if (pending === undefined) {
      pending = setTimeout(() => refresh(doc, { announce: announceRelease }), 0);
    }
  }

  doc.addEventListener(
    "focusout",
    (event) => {
      const next = event.relatedTarget;
      const leaves = (element: HTMLElement) =>
        event.target instanceof Node &&
        element.contains(event.target) &&
        !(next instanceof Node && element.contains(next));
      if (held.some(leaves)) release();
    },
    { signal },
  );
  // focus leaving a rich text field's iframe fires no focusout in this document
  doc.addEventListener(
    "focusin",
    (event) => {
      const target = event.target;
      if (held.length > 0 && target instanceof Node && !held.some((element) => element.contains(target))) release();
    },
    { signal },
  );
  // A page kept in the back-forward cache keeps its timers, so a typed update applies if it is restored.
  doc.defaultView?.addEventListener(
    "pagehide",
    (event) => {
      if (!event.persisted) cancelPending();
    },
    { signal },
  );

  function refresh(
    root: ParentNode = doc,
    { announce: announceChanges = false }: { announce?: boolean } = {},
  ): FieldDisplayResult {
    clearScheduled();
    const result = applyFieldDisplayRules(root, { focused: doc.activeElement });
    held = result.held;
    // a refresh after a save or reload does not undo that the user's change is what hides a held field
    announceRelease = held.length > 0 && (announceChanges || announceRelease);
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
      const change = markAnswered(control);
      if (change === "typed") schedule(TYPING_PAUSE_MS);
      else if (change === "picked") refresh(doc, { announce: true });
    },
    valueRestored() {
      schedule(0);
    },
    cancelPending,
    syncStoredValue(fieldId, value) {
      const element = Array.from(doc.querySelectorAll<HTMLElement>(FIELD_SELECTOR)).find(
        (candidate) => candidate.dataset.fieldId === String(fieldId),
      );
      if (element) syncStoredValue(element, value ?? "");
    },
    shownMandatoryFieldsSatisfied,
    dispose() {
      cancelPending();
      listeners.abort();
      region.remove();
    },
  };
}
