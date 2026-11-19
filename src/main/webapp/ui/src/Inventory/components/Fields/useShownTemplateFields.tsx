import { createContext, type FocusEvent, type ReactNode, useContext, useEffect, useRef, useState } from "react";
import RevealedFieldsAnnouncer from "@/modules/common/field-display/RevealedFieldsAnnouncer";
import type { Field } from "../../../stores/definitions/Field";
import { hiddenFieldsReading, scalarSourceValues } from "../../../stores/models/fieldVisibility";

/** How long typing in a text or number source must pause before the fields it shows or hides move. */
export const SETTLE_DELAY_MS = 300;

/** Overrides `SETTLE_DELAY_MS`, so a browser test can act reliably within the pause. */
export const SettleDelayContext = createContext(SETTLE_DELAY_MS);

const identities = new WeakMap<Field, number>();
let nextIdentity = 0;

/** A number for this field object, so a reload, which builds new field objects, starts a new session. */
function identityOf(field: Field): number {
  let identity = identities.get(field);
  if (identity === undefined) {
    identity = nextIdentity++;
    identities.set(field, identity);
  }
  return identity;
}

/** The text and number source values that what is rendered reads, by position, for one session. */
type Settled = { session: string; typed: string };

/**
 * The template fields of a sample or instrument that display rules leave
 * shown, and a live region announcing the ones an answer shows or hides. Hidden
 * fields keep their values but leave the page, so they also stop counting
 * towards the form section's error state.
 *
 * Only what is rendered and announced waits: the record's own `isFieldHidden`,
 * which saving and mandatory checks read, always follows the current values.
 *  - What is rendered reads each text or number source that a rule reads as it
 *    was when typing last paused for the settle delay, so fields do not jump on
 *    each key and one announcement covers the net change. Options are read as
 *    they are now, so choosing one applies at once.
 *  - A field that contains focus is not hidden until focus leaves it, so focus
 *    never moves on its own. Focus moving into a frame inside the field, such
 *    as a rich text editor's, or out of the window, does not leave it; it is
 *    checked again when the window regains focus or focus lands anywhere else.
 *  - Another record, a reload, or leaving edit mode applies the current
 *    visibility at once; the pending update is dropped, as it is on unmount.
 *
 * Hosts wrap each rendered field with `wrapField`, which tracks focus, and
 * report errors through the returned `onErrorStateChange`. A field that the
 * values hide but focus keeps on screen is not mandatory and reports no error;
 * `hiddenByValues` tells hosts which fields those are.
 */
export default function useShownTemplateFields(
  record: {
    globalId: string | null;
    fields?: ReadonlyArray<Field>;
    isFieldHidden?: (field: Field) => boolean;
    isFieldEditable?: (field: "fields") => boolean;
  },
  onErrorStateChange: (fieldName: string, hasError: boolean) => void,
): {
  shownFields: Array<Field>;
  announcer: ReactNode;
  wrapField: (field: Field, node: ReactNode) => ReactNode;
  hiddenByValues: (field: Field) => boolean;
  onErrorStateChange: (fieldName: string, hasError: boolean) => void;
} {
  const settleDelay = useContext(SettleDelayContext);
  const fields = record.fields ?? [];
  const hidden = fields.filter((field) => record.isFieldHidden?.(field) ?? false);
  const typed = JSON.stringify(scalarSourceValues(fields));
  const session = JSON.stringify([
    record.globalId,
    record.isFieldEditable?.("fields") ?? false,
    fields.map(identityOf),
  ]);

  const [settled, setSettled] = useState<Settled>({ session, typed });
  // another session reads the current values at once
  if (settled.session !== session) setSettled({ session, typed });
  const settledTyped = settled.session === session ? settled.typed : typed;
  // restarted by each key typed; cleared by another session and on unmount
  useEffect(() => {
    if (settledTyped === typed) return;
    const timer = setTimeout(() => setSettled({ session, typed }), settleDelay);
    return () => clearTimeout(timer);
  }, [session, typed, settledTyped, settleDelay]);

  const settledHidden = record.isFieldHidden
    ? [...hiddenFieldsReading(fields, JSON.parse(settledTyped) as Array<string | null>)]
    : [];

  const [focus, setFocus] = useState<{ session: string; field: Field } | null>(null);
  const focused = focus?.session === session ? focus.field : null;
  const blurCheck = useRef<ReturnType<typeof setTimeout>>(undefined);
  /** The element wrapping the focused field. */
  const focusedWrapper = useRef<HTMLElement | null>(null);
  useEffect(() => () => clearTimeout(blurCheck.current), []);

  /** Releases `field` once focus has settled outside it; where focus went is known only once it lands. */
  const checkFocusLeft = (field: Field, wrapper: HTMLElement) => {
    clearTimeout(blurCheck.current);
    blurCheck.current = setTimeout(() => {
      if (!document.hasFocus() || wrapper.contains(document.activeElement)) return;
      setFocus((current) => (current?.field === field ? null : current));
    });
  };

  // a field kept while the window lost focus gets no blur when focus then lands outside it, so look again
  useEffect(() => {
    if (focused === null) return;
    const recheck = () => {
      if (focusedWrapper.current) checkFocusLeft(focused, focusedWrapper.current);
    };
    window.addEventListener("focus", recheck);
    document.addEventListener("focusin", recheck);
    return () => {
      window.removeEventListener("focus", recheck);
      document.removeEventListener("focusin", recheck);
    };
  }, [focused]);

  const renderedHidden = settledHidden.filter((field) => field !== focused);
  const shownFields = fields.filter((field) => !renderedHidden.includes(field));
  const hiddenNames = renderedHidden.map((field) => field.name);
  // the values say these are hidden even while they are still on screen, so they no longer count as errors
  const errorFreeSignature = JSON.stringify([hidden.map((field) => field.name), hiddenNames]);

  // the signature stands for both name lists, which are new arrays on every render
  useEffect(() => {
    for (const name of new Set([...hidden.map((field) => field.name), ...hiddenNames]))
      onErrorStateChange(`template_${name}`, false);
  }, [errorFreeSignature]);

  const wrapField = (field: Field, node: ReactNode) => (
    // biome-ignore lint/a11y/noStaticElementInteractions: it only observes focus moving among the field's own controls
    <div
      key={identityOf(field)}
      onFocus={({ currentTarget }: FocusEvent<HTMLDivElement>) => {
        clearTimeout(blurCheck.current);
        focusedWrapper.current = currentTarget;
        setFocus({ session, field });
      }}
      onBlur={({ currentTarget, relatedTarget }: FocusEvent<HTMLDivElement>) => {
        if (relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) return;
        // focus entering a frame inside the field reports no relatedTarget, so look once it lands
        checkFocusLeft(field, currentTarget);
      }}
    >
      {node}
    </div>
  );

  return {
    shownFields,
    wrapField,
    hiddenByValues: (field) => record.isFieldHidden?.(field) ?? false,
    onErrorStateChange: (fieldName, hasError) => {
      const field = fields.find(({ name }) => `template_${name}` === fieldName);
      onErrorStateChange(fieldName, hasError && !(field && record.isFieldHidden?.(field)));
    },
    announcer: (
      // another session, such as another record or a reload, starts a new announcer, so nothing is announced as changed
      <RevealedFieldsAnnouncer
        key={session}
        shownFieldNames={shownFields.filter((field) => field.displayRule !== null).map((field) => field.name)}
        hiddenFieldNames={hiddenNames}
      />
    ),
  };
}
