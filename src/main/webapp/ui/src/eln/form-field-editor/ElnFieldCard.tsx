import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { memo, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import FieldCard from "@/modules/common/field-editor/FieldCard";
import FieldDisplayCondition from "@/modules/common/field-editor/FieldDisplayCondition";
import type { FieldTypeOption } from "@/modules/common/field-editor/FieldTypeSelect";
import OptionsEditor from "@/modules/common/field-editor/OptionsEditor";
import { type RuleEditorField, ruleGraphFor } from "@/modules/common/field-editor/ruleEditing";
import type { FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import ElnFieldSettings from "./ElnFieldSettings";
import {
  type EditorField,
  type FieldChecks,
  type FieldSettings,
  hasOptions,
  NAME_MAX_LENGTH,
  problemsFor,
} from "./editorModel";
import type { ElnFieldType } from "./formFieldsApi";
import { useProblemMessage } from "./useProblemMessage";

/** What a card can do to the field list. The host keeps the same object for its whole life. */
export type FieldCardActions = {
  rename: (key: string, name: string) => void;
  setMandatory: (key: string, mandatory: boolean) => void;
  retype: (key: string, type: ElnFieldType) => void;
  move: (key: string, position: number) => void;
  remove: (key: string) => void;
  renameOption: (key: string, index: number, value: string) => void;
  removeOption: (key: string, index: number) => void;
  addOption: (key: string) => void;
  changeSettings: (key: string, settings: Partial<FieldSettings>) => void;
  changeRule: (key: string, rule: FieldDisplayRule | null) => void;
  /** Whether the card's condition lists the fields it can read: while it has a draft or its field menu is open. */
  showSources: (key: string, shown: boolean) => void;
};

type ElnFieldCardProps = {
  field: EditorField;
  position: number;
  count: number;
  /** Every field, for the display condition; the same array while only names change. */
  ruleFields: ReadonlyArray<RuleEditorField>;
  /**
   * The other fields' names this card shows: the field its rule reads, the
   * fields that read this one, and, while its condition has a draft or its
   * field menu open, every field a condition can read. Not read here; when one
   * changes, the card renders again.
   */
  shownNames: string;
  /** Which client checks apply, depending on what changed since the field was saved. */
  checks: FieldChecks;
  editable: boolean;
  allTypeOptions: ReadonlyArray<FieldTypeOption<ElnFieldType>>;
  creatableTypeOptions: ReadonlyArray<FieldTypeOption<ElnFieldType>>;
  actions: FieldCardActions;
};

/**
 * One field of the ELN form field editor. Memoised, so typing in one field
 * re-renders only that card and the cards that show its name.
 */
function ElnFieldCard({
  field,
  position,
  count,
  ruleFields,
  checks,
  editable,
  allTypeOptions,
  creatableTypeOptions,
  actions,
}: ElnFieldCardProps): ReactNode {
  const { t } = useTranslation("workspace");
  const problemMessage = useProblemMessage();
  const problems = problemsFor(field, checks);
  const { key } = field;
  const usedOptions = new Set(
    ruleGraphFor(ruleFields)
      .dependentsOf(key)
      .flatMap(({ rule }) => rule?.values ?? []),
  );
  const typeOptions = creatableTypeOptions.some(({ value }) => value === field.type)
    ? creatableTypeOptions
    : allTypeOptions.filter(({ value }) => value === field.type);

  return (
    <Box data-field-key={key}>
      <FieldCard
        name={field.name}
        onNameChange={(name) => actions.rename(key, name)}
        nameError={problemMessage(problems.name) ?? null}
        nameMaxLength={NAME_MAX_LENGTH}
        type={field.type}
        typeOptions={typeOptions}
        onTypeChange={(type) => actions.retype(key, type)}
        canChangeType={field.id === null}
        mandatory={field.mandatory}
        onMandatoryChange={(mandatory) => actions.setMandatory(key, mandatory)}
        position={position}
        count={count}
        onMove={(newPosition) => actions.move(key, newPosition)}
        canDelete={field.canDelete}
        onDelete={() => actions.remove(key)}
        editable={editable}
        displayCondition={
          <FieldDisplayCondition
            fieldKey={key}
            fields={ruleFields}
            onChange={(rule) => actions.changeRule(key, rule)}
            onShowsSourcesChange={actions.showSources}
            editable={editable}
          />
        }
      >
        {/* the host looks here, after the name, for the first problem to focus */}
        <Stack spacing={2} data-field-settings>
          {hasOptions(field) && (
            <Box>
              <OptionsEditor
                options={field.options.map((option) => {
                  const usedByRule = usedOptions.has(option);
                  return {
                    value: option,
                    editable: !usedByRule,
                    lockedReason: usedByRule ? t("formFieldEditor.optionInUse") : undefined,
                  };
                })}
                onOptionChange={(i, value) => actions.renameOption(key, i, value)}
                onOptionRemove={(i) => actions.removeOption(key, i)}
                onOptionAdd={() => actions.addOption(key)}
                disabled={!editable}
              />
              {problems.options && problems.options !== "optionsRequired" && (
                <Typography variant="body2" color="error" sx={{ mt: 1 }}>
                  {problemMessage(problems.options)}
                </Typography>
              )}
            </Box>
          )}
          <ElnFieldSettings
            field={field}
            onChange={(settings) => actions.changeSettings(key, settings)}
            problems={problems}
            editable={editable}
          />
        </Stack>
      </FieldCard>
    </Box>
  );
}

export default memo(ElnFieldCard);
