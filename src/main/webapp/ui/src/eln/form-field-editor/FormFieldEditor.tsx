import AddIcon from "@mui/icons-material/Add";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { type ReactNode, type Ref, useEffect, useId, useImperativeHandle, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useConfirm } from "@/components/ConfirmProvider";
import DisplayRuleSummary from "@/modules/common/field-editor/DisplayRuleSummary";
import DisplayRulesPanel from "@/modules/common/field-editor/DisplayRulesPanel";
import FieldCard from "@/modules/common/field-editor/FieldCard";
import type { FieldTypeOption } from "@/modules/common/field-editor/FieldTypeSelect";
import OptionsEditor from "@/modules/common/field-editor/OptionsEditor";
import { type RuleChange, type RuleEditorField, usableOptions } from "@/modules/common/field-editor/ruleEditing";
import { formatList } from "@/modules/common/i18n/listFormat";
import ElnFieldSettings from "./ElnFieldSettings";
import {
  adoptSavedIds,
  CREATABLE_TYPES,
  type CreatableType,
  canBeSource,
  changeType,
  deleteField,
  dependentsOf,
  type EditorField,
  type FieldSettings,
  fieldProblems,
  fieldsUsingOption,
  hasOptions,
  isNewOrChanged,
  moveField,
  NAME_MAX_LENGTH,
  newField,
  removeOption,
  renameOption,
  savedItems,
  saveSignature,
  updateField,
} from "./editorModel";
import { type ElnFieldType, FormFieldsSaveError } from "./formFieldsApi";
import { useProblemMessage } from "./useProblemMessage";

/** The editor's part of the toolbar handle; see `FormFieldEditorRoot` and `form.js`. */
export type FieldEditorHandle = {
  /**
   * Saves the fields if they changed. Rejects, leaving the editor dirty and
   * showing why, if the save fails. A call while a save is in flight returns
   * that save.
   */
  save: () => Promise<void>;
  isDirty: () => boolean;
  fieldCount: () => number;
  /** Called after the toolbar publishes a new form: its fields then change only through a new version. */
  markPublished: () => void;
};

/** Dispatched on `window` whenever the dirty state or the number of fields changes. */
export const FORM_FIELD_EDITOR_CHANGE_EVENT = "formFieldEditor:change";
export type FormFieldEditorChangeDetail = { dirty: boolean; fieldCount: number };

type Status = { kind: "problems"; field: string } | { kind: "error"; messages: ReadonlyArray<string> } | null;

/** Where focus goes after the next render: a field's name, its first invalid control, or "Add field". */
type FocusTarget = { kind: "name" | "problem"; key: string } | { kind: "add" } | null;

type FormFieldEditorProps = {
  initialFields: ReadonlyArray<EditorField>;
  /** Sends the full field list and resolves with the saved fields. */
  onSave: (fields: ReadonlyArray<EditorField>) => Promise<ReadonlyArray<EditorField>>;
  editable: boolean;
  ref?: Ref<FieldEditorHandle>;
};

function useTypeOptions(): ReadonlyArray<FieldTypeOption<ElnFieldType>> {
  const { t } = useTranslation("workspace");
  return [
    { value: "Number", label: t("formFieldEditor.types.number") },
    { value: "String", label: t("formFieldEditor.types.string") },
    { value: "Text", label: t("formFieldEditor.types.text") },
    { value: "Radio", label: t("formFieldEditor.types.radio") },
    { value: "Choice", label: t("formFieldEditor.types.choice") },
    { value: "Date", label: t("formFieldEditor.types.date") },
    { value: "Time", label: t("formFieldEditor.types.time") },
    { value: "Uri", label: t("formFieldEditor.types.uri") },
    { value: "Reference", label: t("formFieldEditor.types.reference") },
    { value: "Attachment", label: t("formFieldEditor.types.attachment") },
  ];
}

/**
 * Edits an ELN form's fields, their order and their display rules in memory.
 * It has no save button: the legacy toolbar calls `save()` through the handle
 * before it saves, updates or publishes the form.
 */
export default function FormFieldEditor({
  initialFields,
  onSave,
  editable: editableForm,
  ref,
}: FormFieldEditorProps): ReactNode {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const confirm = useConfirm();
  const problemMessage = useProblemMessage();
  const allTypeOptions = useTypeOptions();
  const statusId = useId();
  const [fields, setFields] = useState<ReadonlyArray<EditorField>>(initialFields);
  const [baseline, setBaseline] = useState(() => saveSignature(initialFields));
  const [baselineItems, setBaselineItems] = useState(() => savedItems(initialFields));
  const [status, setStatus] = useState<Status>(null);
  const [addMenuAnchor, setAddMenuAnchor] = useState<HTMLElement | null>(null);
  const [focusTarget, setFocusTarget] = useState<FocusTarget>(null);
  const addButtonRef = useRef<HTMLButtonElement>(null);
  // one save at a time: a second toolbar action waits for the first
  const saveInFlight = useRef<Promise<void> | null>(null);
  // once published from this page, the form's fields can only change through a new version
  const [published, setPublished] = useState(false);
  const editable = editableForm && !published;
  const listRef = useRef<HTMLDivElement>(null);

  const dirty = saveSignature(fields) !== baseline;
  // Stored data that is not touched is not checked, as on the server: options and names
  // written through the forms API may break the editor's rules without blocking other edits.
  const problemsOf = (field: EditorField) => (isNewOrChanged(field, baselineItems) ? fieldProblems(field) : {});
  const firstInvalid = fields.find((field) => Object.keys(problemsOf(field)).length > 0) ?? null;
  // the handle is called from legacy code at any time, so it reads the latest render
  const latest = useRef({ fields, dirty, firstInvalid });
  latest.current = { fields, dirty, firstInvalid };

  const unnamed = t("common:fieldEditor.card.unnamed");
  const nameOf = (field: EditorField) => field.name || unnamed;
  const language = i18n.resolvedLanguage ?? i18n.language;
  const namesOf = (list: ReadonlyArray<EditorField>) => formatList(list.map(nameOf), language);

  useImperativeHandle(ref, () => ({
    isDirty: () => latest.current.dirty,
    fieldCount: () => latest.current.fields.length,
    markPublished: () => setPublished(true),
    save: () => {
      if (saveInFlight.current) return saveInFlight.current;
      if (!latest.current.dirty) return Promise.resolve();
      const invalid = latest.current.firstInvalid;
      if (invalid) {
        setStatus({ kind: "problems", field: nameOf(invalid) });
        setFocusTarget({ kind: "problem", key: invalid.key });
        return Promise.reject(new FormFieldsSaveError([]));
      }
      const sent = latest.current.fields;
      const sentSignature = saveSignature(sent);
      const saving = (async () => {
        try {
          const saved = await onSave(sent);
          const savedSignature = saveSignature(saved);
          // edits made while the save was in flight are kept; they only gain the new fields' ids
          const keep = (current: ReadonlyArray<EditorField>) =>
            saveSignature(current) === sentSignature ? saved : adoptSavedIds(current, saved);
          const next = keep(latest.current.fields);
          // the toolbar may ask again before React renders
          latest.current = { ...latest.current, fields: next, dirty: saveSignature(next) !== savedSignature };
          setFields(keep);
          setBaseline(savedSignature);
          setBaselineItems(savedItems(saved));
          setStatus(null);
        } catch (error) {
          setStatus({
            kind: "error",
            messages:
              error instanceof FormFieldsSaveError && error.messages.length > 0
                ? error.messages
                : [t("formFieldEditor.saveFailed")],
          });
          throw error;
        } finally {
          saveInFlight.current = null;
        }
      })();
      saveInFlight.current = saving;
      return saving;
    },
  }));

  useEffect(() => {
    window.dispatchEvent(
      new CustomEvent<FormFieldEditorChangeDetail>(FORM_FIELD_EDITOR_CHANGE_EVENT, {
        detail: { dirty, fieldCount: fields.length },
      }),
    );
  }, [dirty, fields.length]);

  useEffect(() => {
    if (focusTarget === null) return;
    if (focusTarget.kind === "add") {
      addButtonRef.current?.focus();
    } else {
      const card = listRef.current?.querySelector(`[data-field-key="${focusTarget.key}"]`);
      const name = card?.querySelector<HTMLElement>('input[name="name"]');
      const invalid = card?.querySelector<HTMLElement>('[aria-invalid="true"]');
      (focusTarget.kind === "problem" ? (invalid ?? name) : name)?.focus();
    }
    setFocusTarget(null);
  }, [focusTarget]);

  const update = (key: string, change: (field: EditorField) => EditorField) =>
    setFields((current) => updateField(current, key, change));

  const confirmDependents = async (
    dependents: ReadonlyArray<EditorField>,
    title: string,
    confirmLabel: string,
  ): Promise<boolean> =>
    dependents.length === 0 ||
    confirm(
      title,
      t("formFieldEditor.confirm.dependents", {
        count: dependents.length,
        fields: namesOf(dependents),
      }),
      confirmLabel,
      t("common:actions.cancel"),
    );

  const remove = async (field: EditorField) => {
    if (
      await confirmDependents(
        dependentsOf(fields, field.key),
        t("formFieldEditor.confirm.deleteField", { field: nameOf(field) }),
        t("common:actions.delete"),
      )
    ) {
      const next = fields[fields.indexOf(field) + 1];
      setFields((current) => deleteField(current, field.key));
      setFocusTarget(next ? { kind: "name", key: next.key } : { kind: "add" });
    }
  };

  const retype = async (field: EditorField, type: ElnFieldType) => {
    if (!(CREATABLE_TYPES as ReadonlyArray<string>).includes(type)) return;
    if (
      await confirmDependents(
        canBeSource(type) ? [] : dependentsOf(fields, field.key),
        t("formFieldEditor.confirm.changeType", { field: nameOf(field) }),
        t("formFieldEditor.confirm.changeTypeAction"),
      )
    )
      setFields((current) => changeType(current, field.key, type as CreatableType));
  };

  const dropOption = async (field: EditorField, index: number) => {
    if (!hasOptions(field)) return;
    const option = field.options[index] ?? "";
    const stillOffered = field.options.some((other, i) => i !== index && other === option);
    if (
      await confirmDependents(
        stillOffered ? [] : fieldsUsingOption(fields, field.key, option),
        t("formFieldEditor.confirm.removeOption", { option }),
        t("common:actions.remove"),
      )
    )
      setFields((current) => removeOption(current, field.key, index));
  };

  const ruleFields: Array<RuleEditorField> = fields.map((field) => ({
    key: field.key,
    name: nameOf(field),
    canBeSource: canBeSource(field.type),
    options: hasOptions(field) ? usableOptions(field.options) : [],
    rule: field.rule,
  }));

  const applyRuleChanges = (changes: ReadonlyArray<RuleChange>) =>
    setFields((current) =>
      current.map((field) => {
        const change = changes.find(({ key }) => key === field.key);
        return change ? { ...field, rule: change.rule } : field;
      }),
    );

  const creatableOptions = allTypeOptions.filter(({ value }) =>
    (CREATABLE_TYPES as ReadonlyArray<string>).includes(value),
  );

  return (
    <Stack spacing={2} sx={{ my: 2 }}>
      <Typography variant="h5" component="h2">
        {t("formFieldEditor.heading")}
      </Typography>
      {status !== null && (
        <Alert severity="error" role="alert" aria-labelledby={statusId} onClose={() => setStatus(null)}>
          <AlertTitle id={statusId}>
            {status.kind === "problems" ? t("formFieldEditor.problemsTitle") : t("formFieldEditor.errorTitle")}
          </AlertTitle>
          {status.kind === "problems" && (
            <Typography variant="body2">{t("formFieldEditor.firstProblem", { field: status.field })}</Typography>
          )}
          {status.kind === "error" && (
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              {status.messages.map((message) => (
                <li key={message}>{message}</li>
              ))}
            </Box>
          )}
        </Alert>
      )}
      {published && <Alert severity="info">{t("formFieldEditor.publishedNotice")}</Alert>}
      {fields.length > 0 && <DisplayRulesPanel fields={ruleFields} onChange={applyRuleChanges} editable={editable} />}
      {fields.length === 0 && (
        <Typography variant="body1" color="text.secondary">
          {t("formFieldEditor.noFields")}
        </Typography>
      )}
      <Stack spacing={2} ref={listRef}>
        {fields.map((field, index) => {
          const problems = problemsOf(field);
          const source = field.rule ? (fields.find((other) => other.key === field.rule?.sourceFieldKey) ?? null) : null;
          const typeOptions = creatableOptions.some(({ value }) => value === field.type)
            ? creatableOptions
            : allTypeOptions.filter(({ value }) => value === field.type);
          return (
            <Box key={field.key} data-field-key={field.key}>
              <FieldCard
                name={field.name}
                onNameChange={(name) => update(field.key, (f) => ({ ...f, name }))}
                nameError={problemMessage(problems.name) ?? null}
                nameMaxLength={NAME_MAX_LENGTH}
                type={field.type}
                typeOptions={typeOptions}
                onTypeChange={(type) => void retype(field, type)}
                canChangeType={field.id === null}
                mandatory={field.mandatory}
                onMandatoryChange={(mandatory) => update(field.key, (f) => ({ ...f, mandatory }))}
                position={index}
                count={fields.length}
                onMove={(position) => setFields((current) => moveField(current, field.key, position))}
                canDelete={field.canDelete}
                onDelete={() => void remove(field)}
                editable={editable}
                summary={
                  <DisplayRuleSummary
                    rule={field.rule}
                    sourceName={source ? nameOf(source) : null}
                    sourceIsBelow={source !== null && fields.indexOf(source) > index}
                  />
                }
              >
                {hasOptions(field) && (
                  <Box>
                    <OptionsEditor
                      options={field.options.map((option) => {
                        const usedByRule = fieldsUsingOption(fields, field.key, option).length > 0;
                        return {
                          value: option,
                          editable: !usedByRule,
                          lockedReason: usedByRule ? t("formFieldEditor.optionInUse") : undefined,
                        };
                      })}
                      onOptionChange={(i, value) => setFields((current) => renameOption(current, field.key, i, value))}
                      onOptionRemove={(i) => void dropOption(field, i)}
                      onOptionAdd={() =>
                        update(field.key, (f) => (hasOptions(f) ? { ...f, options: [...f.options, ""] } : f))
                      }
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
                  onChange={(settings: Partial<FieldSettings>) =>
                    update(field.key, (f) => ({ ...f, ...settings, type: f.type }) as EditorField)
                  }
                  problems={problems}
                  editable={editable}
                />
              </FieldCard>
            </Box>
          );
        })}
      </Stack>
      {editable && (
        <Box>
          <Button
            ref={addButtonRef}
            variant="outlined"
            startIcon={<AddIcon />}
            aria-haspopup="menu"
            aria-expanded={Boolean(addMenuAnchor)}
            onClick={({ currentTarget }) => setAddMenuAnchor(currentTarget)}
          >
            {t("formFieldEditor.addField")}
          </Button>
          <Menu anchorEl={addMenuAnchor} open={Boolean(addMenuAnchor)} onClose={() => setAddMenuAnchor(null)}>
            {creatableOptions.map(({ value, label }) => (
              <MenuItem
                key={value}
                onClick={() => {
                  setAddMenuAnchor(null);
                  const field = newField(value as CreatableType);
                  setFields((current) => [...current, field]);
                  setFocusTarget({ kind: "name", key: field.key });
                }}
              >
                {label}
              </MenuItem>
            ))}
          </Menu>
        </Box>
      )}
    </Stack>
  );
}
