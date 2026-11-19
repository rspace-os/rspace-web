import { type FocusEvent, type ReactNode, useEffect, useRef, useState } from "react";
import RevealedFieldsAnnouncer from "@/modules/common/field-display/RevealedFieldsAnnouncer";
import type { Field } from "../../../stores/definitions/Field";
import { ruleSourceValue } from "../../../stores/models/fieldVisibility";

/** How long typing in a text or number source must pause before the fields it shows or hides move. */
export const SETTLE_DELAY_MS = 300;

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

function sameMembers(a: ReadonlyArray<Field>, b: ReadonlyArray<Field>): boolean {
  return a.length === b.length && a.every((field) => b.includes(field));
}

type Settled = { session: string; typed: string; hidden: ReadonlyArray<Field> };

/**
 * The template fields of a sample or instrument that display rules leave
 * shown, and a live region announcing the ones an answer shows or hides. Hidden
 * fields keep their values but leave the page, so they also stop counting
 * towards the form section's error state.
 *
 * Only what is rendered and announced waits: the record's own `isFieldHidden`,
 * which saving and mandatory checks read, always follows the current values.
 *  - Typing in a text or number source moves the fields it governs only once
 *    typing pauses for `SETTLE_DELAY_MS`, so fields do not jump on each key,
 *    and one announcement covers the net change. Choosing an option applies at
 *    once.
 *  - A field that contains focus is not hidden until focus leaves it, so focus
 *    never moves on its own.
 *  - Another record, a reload, or leaving edit mode applies the current
 *    visibility at once; the pending update is dropped, as it is on unmount.
 *
 * Hosts wrap each rendered field with `wrapField`, which tracks focus.
 */
export default function useShownTemplateFields(
  record: {
    globalId: string | null;
    fields?: ReadonlyArray<Field>;
    isFieldHidden?: (field: Field) => boolean;
    isFieldEditable?: (field: "fields") => boolean;
  },
  onErrorStateChange: (fieldName: string, hasError: boolean) => void,
): { shownFields: Array<Field>; announcer: ReactNode; wrapField: (field: Field, node: ReactNode) => ReactNode } {
  const fields = record.fields ?? [];
  const hidden = fields.filter((field) => record.isFieldHidden?.(field) ?? false);
  const typed = JSON.stringify(fields.map(ruleSourceValue));
  const session = JSON.stringify([
    record.globalId,
    record.isFieldEditable?.("fields") ?? false,
    fields.map(identityOf),
  ]);

  const [settled, setSettled] = useState<Settled>({ session, typed, hidden });
  const [focused, setFocused] = useState<Field | null>(null);
  let settledHidden: ReadonlyArray<Field>;
  if (settled.session !== session || settled.typed === typed) {
    // nothing typed is pending, so what is rendered follows the values; recorded for when typing starts
    settledHidden = hidden;
    if (settled.session !== session || !sameMembers(settled.hidden, hidden)) setSettled({ session, typed, hidden });
  } else {
    settledHidden = settled.hidden;
  }

  const latest = useRef<Settled>({ session, typed, hidden });
  useEffect(() => {
    latest.current = { session, typed, hidden };
  });
  // restarted by each key typed; cleared by another session and on unmount
  useEffect(() => {
    if (settled.session !== session || settled.typed === typed) return;
    const timer = setTimeout(() => setSettled(latest.current), SETTLE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [session, typed, settled.session, settled.typed]);

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
      onFocus={() => setFocused(field)}
      onBlur={({ currentTarget, relatedTarget }: FocusEvent<HTMLDivElement>) => {
        if (relatedTarget instanceof Node && currentTarget.contains(relatedTarget)) return;
        setFocused((current) => (current === field ? null : current));
      }}
    >
      {node}
    </div>
  );

  return {
    shownFields,
    wrapField,
    announcer: (
      // another record starts a new announcer, so its fields are not announced as revealed
      <RevealedFieldsAnnouncer
        key={record.globalId ?? "unsaved"}
        shownFieldNames={shownFields.filter((field) => field.displayRule !== null).map((field) => field.name)}
        hiddenFieldNames={hiddenNames}
      />
    ),
  };
}
