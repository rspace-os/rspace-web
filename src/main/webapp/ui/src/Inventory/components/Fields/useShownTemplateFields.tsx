import { type ReactNode, useEffect } from "react";
import RevealedFieldsAnnouncer from "@/modules/common/field-display/RevealedFieldsAnnouncer";
import type { Field } from "../../../stores/definitions/Field";

/**
 * The template fields of a sample or instrument that display rules leave
 * shown, and a live region announcing the ones an answer reveals. Hidden
 * fields keep their values but leave the page, so they also stop counting
 * towards the form section's error state.
 */
export default function useShownTemplateFields(
  record: { fields?: ReadonlyArray<Field>; isFieldHidden?: (field: Field) => boolean },
  onErrorStateChange: (fieldName: string, hasError: boolean) => void,
): { shownFields: Array<Field>; announcer: ReactNode } {
  const fields = record.fields ?? [];
  const shownFields = fields.filter((field) => !record.isFieldHidden?.(field));
  const hiddenNames = fields.filter((field) => !shownFields.includes(field)).map((field) => field.name);
  const hiddenSignature = JSON.stringify(hiddenNames);

  // the signature stands for hiddenNames, which is a new array on every render
  useEffect(() => {
    for (const name of hiddenNames) onErrorStateChange(`template_${name}`, false);
  }, [hiddenSignature]);

  return {
    shownFields,
    announcer: (
      <RevealedFieldsAnnouncer
        shownFieldNames={shownFields.filter((field) => field.displayRule !== null).map((field) => field.name)}
      />
    ),
  };
}
