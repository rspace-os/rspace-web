import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import DisplayConditionsNote from "@/modules/common/field-editor/DisplayConditionsNote";
import type { Id } from "../../../stores/definitions/BaseRecord";
import type { Field } from "../../../stores/definitions/Field";
import FieldModel from "../../../stores/models/FieldModel";
import CustomField from "./CustomField";

type TemplateFieldListArgs = {
  record: {
    readonly fields: ReadonlyArray<Field>;
    readonly globalId: string | null;
    readonly state: string;
    removeCustomField(id: Id, index: number, deleteFromRecords?: boolean): void;
    moveField(field: Field, newIndex: number): void;
  };
  editable: boolean;
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
  forceColumnLayout: boolean;
  recordTypeName?: "sample" | "instrument";
};

/**
 * The field cards of a sample or instrument template. A component of its own,
 * not one made while the host renders, so the cards, and any display condition
 * still being drafted in them, stay mounted when fields are added or moved.
 */
function FieldCards({
  record,
  editable,
  onErrorStateChange,
  forceColumnLayout,
  recordTypeName,
}: TemplateFieldListArgs): ReactNode {
  return (
    <Stack spacing={2}>
      {editable && <DisplayConditionsNote />}
      {record.fields
        .filter((field): field is FieldModel => field instanceof FieldModel)
        .map((field: FieldModel, i: number) => (
          <CustomField
            field={field}
            i={i}
            key={field.fieldKey ?? i}
            editable={editable}
            onErrorStateChange={(value) => onErrorStateChange(field.globalId ?? "NEW", value)}
            onConditionErrorStateChange={(value) => onErrorStateChange(`displayCondition:${field.fieldKey}`, value)}
            onRemove={(deleteFromRecords = false) =>
              record.removeCustomField(field.id, record.fields.indexOf(field), deleteFromRecords)
            }
            forceColumnLayout={forceColumnLayout}
            onMove={(index) => record.moveField(field, index)}
            recordTypeName={recordTypeName}
          />
        ))}
    </Stack>
  );
}

const ObservedFieldCards = observer(FieldCards);

const recordIds = new WeakMap<object, number>();
let nextRecordId = 0;

/**
 * Each edit session gets fresh cards: another record, even one whose fields
 * have the same keys (a copy, say), the same record once it is created, or
 * once editing ends or starts. A save that fails keeps the session, and so
 * the drafts.
 */
function TemplateFieldList(props: TemplateFieldListArgs): ReactNode {
  let id = recordIds.get(props.record);
  if (id === undefined) {
    nextRecordId += 1;
    id = nextRecordId;
    recordIds.set(props.record, id);
  }
  return <ObservedFieldCards key={`${id}:${props.record.globalId}:${props.record.state}`} {...props} />;
}

export default observer(TemplateFieldList);
