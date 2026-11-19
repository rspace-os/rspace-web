import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import type { TFunction } from "i18next";
import { computed } from "mobx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import FieldCard from "@/modules/common/field-editor/FieldCard";
import SharedFieldDisplayCondition from "@/modules/common/field-editor/FieldDisplayCondition";
import type { FieldTypeOption } from "@/modules/common/field-editor/FieldTypeSelect";
import OptionsEditor from "@/modules/common/field-editor/OptionsEditor";
import { formatList } from "@/modules/common/i18n/listFormat";
import RemoveButton from "../../../components/RemoveButton";
import type { FieldType as ApiFieldType } from "../../../stores/definitions/Field";
import type FieldModel from "../../../stores/models/FieldModel";
import { hasOptions } from "../../../stores/models/FieldModel";
import { FIELD_DATA, FIELD_LABEL, fieldTypeToApiString, SUPPORTED_TYPES } from "../../../stores/models/FieldTypes";
import { sourceKindOfType } from "../../../stores/models/fieldVisibility";
import useStores from "../../../stores/use-stores";
import RemoveMenu, { type DeleteOption } from "../../components/Fields/RemoveMenu";
import DefaultValueField from "./DefaultValueField";
import { templateRuleFields } from "./templateRuleFields";

function makeDeleteOptions(t: TFunction<"inventory">, recordType: string): Array<DeleteOption> {
  return [
    { value: false, label: t("fields.templateFields.customField.deleteOptions.keep", { recordType }) },
    { value: true, label: t("fields.templateFields.customField.deleteOptions.remove", { recordType }) },
  ];
}

const TYPE_OPTIONS: ReadonlyArray<FieldTypeOption<ApiFieldType>> = [...SUPPORTED_TYPES].map((fieldType) => ({
  value: fieldTypeToApiString(fieldType) as ApiFieldType,
  label: FIELD_DATA[fieldType].label,
  description: FIELD_DATA[fieldType].help,
  icon: FIELD_DATA[fieldType].icon,
}));

const NAME_MAX_LENGTH = 50;

// observed on its own, so a rename re-renders only the conditions that show that name
const ObservedFieldDisplayCondition = observer(SharedFieldDisplayCondition);

/**
 * Reads the template's rule fields here rather than in the card, so an edit to
 * any field's options, type or rule re-renders the display conditions, not
 * every card. An unfinished condition blocks the save through the field's
 * validation and marks the section as in error.
 */
const FieldDisplayCondition = observer(function FieldDisplayCondition({
  field,
  fieldKey,
  editable,
  onErrorStateChange,
}: {
  field: FieldModel;
  fieldKey: string;
  editable: boolean;
  onErrorStateChange: (value: boolean) => void;
}): React.ReactNode {
  return (
    <ObservedFieldDisplayCondition
      fieldKey={fieldKey}
      fields={templateRuleFields(field.owner)}
      onChange={(rule) => field.setDisplayRule(rule)}
      onIncompleteChange={(incomplete) => {
        field.setDisplayConditionIncomplete(incomplete);
        onErrorStateChange(incomplete);
      }}
      editable={editable}
    />
  );
});

type CustomFieldArgs = {
  field: FieldModel;
  i: number;
  editable: boolean;
  onErrorStateChange: (value: boolean) => void;
  /** Kept apart from `onErrorStateChange`, so a fixed name does not clear an unfinished condition. */
  onConditionErrorStateChange: (value: boolean) => void;
  onRemove: (b?: boolean) => void;
  forceColumnLayout: boolean;
  onMove: (index: number) => void;
  recordTypeName?: "sample" | "instrument";
};

/**
 * Adapts a MobX template field to the shared field card, and guards the edits
 * that would leave display rules reading a field or option that is gone.
 */
function CustomField({
  field,
  i,
  editable,
  onErrorStateChange,
  onConditionErrorStateChange,
  onRemove,
  forceColumnLayout,
  onMove,
  recordTypeName = "sample",
}: CustomFieldArgs): React.ReactNode {
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const { uiStore } = useStores();
  const recordType = t(`recordTypes.${recordTypeName}.lowerPlural`);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const nameOf = (other: FieldModel) => other.name || t("common:fieldEditor.card.unnamed");
  const namesOf = (fields: ReadonlyArray<FieldModel>) => formatList(fields.map(nameOf), language);
  // a computed, so the card re-renders when its own name becomes or stops being a duplicate, not on every rename
  const duplicateName = useMemo(
    () => computed(() => field.owner.fieldNamesInUse.filter((name) => name === field.name).length > 1),
    [field],
  ).get();

  if (field.deleteFieldRequest) {
    return (
      <div data-testid="TemplateField">
        <Paper variant="outlined">
          <Box sx={{ p: 2 }}>
            <Typography variant="subtitle1">
              <strong>{field.name}</strong>{" "}
              {t("template.fields.customField.deleteField", { fieldType: FIELD_LABEL[field.fieldType] })}
            </Typography>
            <p>
              {t("template.fields.customField.newSamplesExclusion", { recordType })}{" "}
              {field.deleteFieldOnSampleUpdate
                ? t("template.fields.customField.deleteFieldOnUpdate", { recordType })
                : t("template.fields.customField.deleteFieldOnUpdateNot", { recordType })}
            </p>
          </Box>
        </Paper>
      </div>
    );
  }

  const nameError =
    field.name === ""
      ? t("fields.templateFields.customField.validation.emptyName")
      : duplicateName
        ? t("fields.templateFields.customField.validation.duplicateName")
        : field.name.length > NAME_MAX_LENGTH
          ? t("fields.templateFields.customField.validation.nameTooLong")
          : null;

  const remove = async (deleteFromRecords?: boolean) => {
    const dependents = field.dependentFields;
    if (
      dependents.length > 0 &&
      !(await uiStore.confirm(
        t("template.displayRules.confirmDeleteField.title", { field: nameOf(field) }),
        t("template.displayRules.confirmDeleteField.message", {
          count: dependents.length,
          fields: namesOf(dependents),
        }),
        t("common:actions.delete"),
        t("common:actions.cancel"),
      ))
    )
      return;
    // a removed field's name no longer needs fixing, so it stops counting towards the section's errors
    onErrorStateChange(false);
    onRemove(deleteFromRecords);
  };

  const changeType = async (type: ApiFieldType) => {
    const dependents = sourceKindOfType(type) === sourceKindOfType(field.type) ? [] : field.dependentFields;
    if (
      dependents.length > 0 &&
      !(await uiStore.confirm(
        t("template.displayRules.confirmTypeChange.title", { field: nameOf(field) }),
        t("template.displayRules.confirmTypeChange.message", {
          count: dependents.length,
          fields: namesOf(dependents),
        }),
        t("template.displayRules.confirmTypeChange.confirm"),
        t("common:actions.cancel"),
      ))
    )
      return;
    field.changeType(type);
  };

  const removeOption = async (index: number) => {
    const option = field.options[index]?.value ?? "";
    const stillOffered = field.options.some((other, otherIndex) => otherIndex !== index && other.value === option);
    const dependents = stillOffered ? [] : field.fieldsUsingOption(option);
    if (
      dependents.length > 0 &&
      !(await uiStore.confirm(
        t("template.displayRules.confirmOptionRemoval.title", { option }),
        t("template.displayRules.confirmOptionRemoval.message", {
          count: dependents.length,
          fields: namesOf(dependents),
        }),
        t("common:actions.remove"),
        t("common:actions.cancel"),
      ))
    )
      return;
    field.removeOption(index);
  };

  return (
    <div data-testid="TemplateField">
      <FieldCard
        name={field.name}
        onNameChange={(name) => {
          field.setAttributesDirty({ name });
          onErrorStateChange(name === "" || name.length > NAME_MAX_LENGTH);
        }}
        nameError={nameError}
        nameMaxLength={NAME_MAX_LENGTH}
        type={field.type}
        typeOptions={TYPE_OPTIONS}
        onTypeChange={(type) => void changeType(type)}
        canChangeType={field.initial}
        mandatory={field.mandatory}
        onMandatoryChange={(mandatory) => field.setAttributesDirty({ mandatory })}
        position={i}
        count={field.owner.fields.length}
        onMove={onMove}
        canDelete
        onDelete={() => void remove()}
        deleteControl={
          field.initial ? (
            <RemoveButton onClick={() => void remove()} title={t("fields.templateFields.customField.deleteNewField")} />
          ) : (
            <RemoveMenu
              deleteOptions={makeDeleteOptions(t, recordType)}
              onClick={(deleteFromRecords) => void remove(deleteFromRecords)}
              tooltipTitle={t("fields.templateFields.customField.deleteField", { fieldName: field.name })}
            />
          )
        }
        editable={editable}
        columnLayout={forceColumnLayout}
        displayCondition={
          field.fieldKey !== null && (
            <FieldDisplayCondition
              field={field}
              fieldKey={field.fieldKey}
              editable={editable}
              onErrorStateChange={onConditionErrorStateChange}
            />
          )
        }
      >
        {hasOptions(field.type) && (
          <OptionsEditor
            options={field.options.map((option) => {
              const usedByRule = field.fieldsUsingOption(option.value).length > 0;
              // saved options cannot be renamed anyway, so only a new option needs the reason
              return {
                value: option.value,
                editable: option.editing && !usedByRule,
                lockedReason:
                  editable && option.editing && usedByRule ? t("template.displayRules.optionInUse") : undefined,
              };
            })}
            onOptionChange={(index, value) => field.renameOption(index, value)}
            onOptionRemove={(index) => void removeOption(index)}
            onOptionAdd={() =>
              field.setAttributesDirty({ options: [...field.options, { value: "", label: "", editing: true }] })
            }
            disabled={!editable}
          />
        )}
        <DefaultValueField field={field} editing={editable} recordTypeName={recordTypeName} />
      </FieldCard>
    </div>
  );
}

export default observer(CustomField);
