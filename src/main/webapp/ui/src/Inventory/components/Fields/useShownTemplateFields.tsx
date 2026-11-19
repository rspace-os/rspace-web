import { partition } from "es-toolkit";
import { type ReactNode, useEffect } from "react";
import RevealedFieldsAnnouncer from "@/modules/common/field-display/RevealedFieldsAnnouncer";
import type { Field } from "../../../stores/definitions/Field";

/**
 * The template fields of a sample or instrument that display rules leave
 * shown, and a live region announcing the ones an answer shows or hides. Hidden
 * fields keep their values but leave the page, so they also stop counting
 * towards the form section's error state.
 */
export default function useShownTemplateFields(
  record: { globalId: string | null; fields?: ReadonlyArray<Field>; isFieldHidden?: (field: Field) => boolean },
  onErrorStateChange: (fieldName: string, hasError: boolean) => void,
): { shownFields: Array<Field>; announcer: ReactNode } {
  const [hiddenFields, shownFields] = partition(record.fields ?? [], (field) => record.isFieldHidden?.(field) ?? false);
  const hiddenNames = hiddenFields.map((field) => field.name);
  const hiddenSignature = JSON.stringify(hiddenNames);

  // the signature stands for hiddenNames, which is a new array on every render
  useEffect(() => {
    for (const name of hiddenNames) onErrorStateChange(`template_${name}`, false);
  }, [hiddenSignature]);

  return {
    shownFields,
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
