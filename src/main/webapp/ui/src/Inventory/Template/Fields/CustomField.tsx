import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import type { TFunction } from "i18next";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import DisplayRuleSummary from "@/modules/common/field-editor/DisplayRuleSummary";
import FieldCard from "@/modules/common/field-editor/FieldCard";
import type { FieldTypeOption } from "@/modules/common/field-editor/FieldTypeSelect";
import OptionsEditor from "@/modules/common/field-editor/OptionsEditor";
import { formatList } from "@/modules/common/i18n/listFormat";
import RemoveButton from "../../../components/RemoveButton";
import type { FieldType as ApiFieldType } from "../../../stores/definitions/Field";
import FieldModel, { hasOptions } from "../../../stores/models/FieldModel";
import { FIELD_DATA, FIELD_LABEL, fieldTypeToApiString, SUPPORTED_TYPES } from "../../../stores/models/FieldTypes";
import useStores from "../../../stores/use-stores";
import RemoveMenu, { type DeleteOption } from "../../components/Fields/RemoveMenu";
import DefaultValueField from "./DefaultValueField";

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

type CustomFieldArgs = {
  field: FieldModel;
  i: number;
  editable: boolean;
  onErrorStateChange: (value: boolean) => void;
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

  const siblings = (field.owner.fields ?? []).filter(
    (other): other is FieldModel => other instanceof FieldModel && !other.deleteFieldRequest,
  );
  const rule = field.displayRule;
  const source = rule ? (siblings.find((other) => other.fieldKey === rule.sourceFieldKey) ?? null) : null;

  const duplicateName = field.owner.fieldNamesInUse.filter((name) => name === field.name).length > 1;
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
    onRemove(deleteFromRecords);
  };

  const changeType = async (type: ApiFieldType) => {
    const dependents = hasOptions(type) ? [] : field.dependentFields;
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
        summary={
          <DisplayRuleSummary
            rule={rule}
            sourceName={source ? nameOf(source) : null}
            sourceIsBelow={source !== null && siblings.indexOf(source) > siblings.indexOf(field)}
          />
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
