import AddIcon from "@mui/icons-material/Add";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import {
  type ReactNode,
  type Ref,
  useCallback,
  useEffect,
  useId,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { useConfirm } from "@/components/ConfirmProvider";
import DisplayConditionsNote from "@/modules/common/field-editor/DisplayConditionsNote";
import type { FieldTypeOption } from "@/modules/common/field-editor/FieldTypeSelect";
import { type RuleEditorField, ruleGraphFor } from "@/modules/common/field-editor/ruleEditing";
import { formatList } from "@/modules/common/i18n/listFormat";
import ElnFieldCard, { type FieldCardActions } from "./ElnFieldCard";
import {
  adoptSavedIds,
  CREATABLE_TYPES,
  type CreatableType,
  canBeSource,
  changeType,
  deleteField,
  dependentsOf,
  type EditorField,
  fieldProblems,
  fieldsUsingOption,
  hasOptions,
  isNewOrChanged,
  moveField,
  newField,
  removeOption,
  renameOption,
  savedItems,
  saveSignature,
  updateField,
} from "./editorModel";
import { elnRuleFields } from "./elnRuleFields";
import { type ElnFieldType, FormFieldsConflictError, FormFieldsSaveError } from "./formFieldsApi";

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
  /**
   * Holds the fields read-only while the toolbar publishes: from the field save, through the
   * access dialog, until the publish request completes. Released if publishing is cancelled or fails.
   */
  setReadOnly: (readOnly: boolean) => void;
};

/** Dispatched on `window` whenever the dirty state or the number of fields changes. */
export const FORM_FIELD_EDITOR_CHANGE_EVENT = "formFieldEditor:change";
export type FormFieldEditorChangeDetail = { dirty: boolean; fieldCount: number };

type Status =
  | { kind: "problems"; field: string }
  | { kind: "error"; messages: ReadonlyArray<string> }
  /** The fields changed elsewhere; saving again would overwrite them, so the author has to reload. */
  | { kind: "conflict" }
  | null;

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
  // the same array while the language is the same, so the field cards need not render again
  return useMemo(
    () => [
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
    ],
    [t],
  );
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
  const [heldReadOnly, setHeldReadOnly] = useState(false);
  const editable = editableForm && !published && !heldReadOnly;
  const listRef = useRef<HTMLDivElement>(null);

  const dirty = saveSignature(fields) !== baseline;
  // Stored data that is not touched is not checked, as on the server: options and names
  // written through the forms API may break the editor's rules without blocking other edits.
  const checked = fields.map((field) => isNewOrChanged(field, baselineItems));
  const firstInvalid = fields.find((field, i) => checked[i] && Object.keys(fieldProblems(field)).length > 0) ?? null;
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
    setReadOnly: (readOnly) => setHeldReadOnly(readOnly),
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
          if (error instanceof FormFieldsConflictError) {
            setStatus({ kind: "conflict" });
            throw error;
          }
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
    // while held read-only the inputs are not there; the target waits until editing resumes
    if (focusTarget === null || !editable) return;
    if (focusTarget.kind === "add") {
      addButtonRef.current?.focus();
    } else {
      const card = listRef.current?.querySelector(`[data-field-key="${focusTarget.key}"]`);
      const name = card?.querySelector<HTMLElement>('input[name="name"]');
      // a display condition still being drafted is not a problem with the field
      const invalid =
        name?.getAttribute("aria-invalid") === "true"
          ? name
          : card?.querySelector<HTMLElement>('[data-field-settings] [aria-invalid="true"]');
      (focusTarget.kind === "problem" ? (invalid ?? name) : name)?.focus();
    }
    setFocusTarget(null);
  }, [focusTarget, editable]);

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

  /* Each reads the fields when it runs, so the cards can be given one object for good. */
  const byKey = (key: string) => latest.current.fields.find((field) => field.key === key);

  const remove = async (key: string) => {
    const current = latest.current.fields;
    const field = byKey(key);
    if (!field) return;
    if (
      await confirmDependents(
        dependentsOf(current, key),
        t("formFieldEditor.confirm.deleteField", { field: nameOf(field) }),
        t("common:actions.delete"),
      )
    ) {
      const next = current[current.indexOf(field) + 1];
      setFields((now) => deleteField(now, key));
      setFocusTarget(next ? { kind: "name", key: next.key } : { kind: "add" });
    }
  };

  const retype = async (key: string, type: ElnFieldType) => {
    const field = byKey(key);
    if (!field || !(CREATABLE_TYPES as ReadonlyArray<string>).includes(type)) return;
    if (
      await confirmDependents(
        canBeSource(type) ? [] : dependentsOf(latest.current.fields, key),
        t("formFieldEditor.confirm.changeType", { field: nameOf(field) }),
        t("formFieldEditor.confirm.changeTypeAction"),
      )
    )
      setFields((current) => changeType(current, key, type as CreatableType));
  };

  const dropOption = async (key: string, index: number) => {
    const field = byKey(key);
    if (!field || !hasOptions(field)) return;
    const option = field.options[index] ?? "";
    const stillOffered = field.options.some((other, i) => i !== index && other === option);
    if (
      await confirmDependents(
        stillOffered ? [] : fieldsUsingOption(latest.current.fields, key, option),
        t("formFieldEditor.confirm.removeOption", { option }),
        t("common:actions.remove"),
      )
    )
      setFields((current) => removeOption(current, key, index));
  };

  const handlers: FieldCardActions = {
    rename: (key, name) => update(key, (f) => ({ ...f, name })),
    setMandatory: (key, mandatory) => update(key, (f) => ({ ...f, mandatory })),
    retype: (key, type) => void retype(key, type),
    move: (key, position) => setFields((current) => moveField(current, key, position)),
    remove: (key) => void remove(key),
    renameOption: (key, index, value) => setFields((current) => renameOption(current, key, index, value)),
    removeOption: (key, index) => void dropOption(key, index),
    addOption: (key) => update(key, (f) => (hasOptions(f) ? { ...f, options: [...f.options, ""] } : f)),
    changeSettings: (key, settings) => update(key, (f) => ({ ...f, ...settings, type: f.type }) as EditorField),
    changeRule: (key, rule) => update(key, (f) => ({ ...f, rule })),
  };
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;
  const [actions] = useState(
    (): FieldCardActions => ({
      rename: (...args) => handlersRef.current.rename(...args),
      setMandatory: (...args) => handlersRef.current.setMandatory(...args),
      retype: (...args) => handlersRef.current.retype(...args),
      move: (...args) => handlersRef.current.move(...args),
      remove: (...args) => handlersRef.current.remove(...args),
      renameOption: (...args) => handlersRef.current.renameOption(...args),
      removeOption: (...args) => handlersRef.current.removeOption(...args),
      addOption: (...args) => handlersRef.current.addOption(...args),
      changeSettings: (...args) => handlersRef.current.changeSettings(...args),
      changeRule: (...args) => handlersRef.current.changeRule(...args),
    }),
  );

  // Names are read when shown, so a rename keeps the same rule fields and the candidates are not worked out again.
  const namesRef = useRef<ReadonlyMap<string, string>>(new Map());
  namesRef.current = new Map(fields.map((field) => [field.key, field.name]));
  const nameByKey = useCallback((key: string) => namesRef.current.get(key) ?? "", []);
  const ruleFieldsRef = useRef<ReadonlyArray<RuleEditorField>>([]);
  ruleFieldsRef.current = elnRuleFields(fields, nameByKey, ruleFieldsRef.current);
  const ruleFields = ruleFieldsRef.current;
  const graph = ruleGraphFor(ruleFields);
  /** The names a field's card shows besides its own: the field its rule reads, and the fields that read it. */
  const shownNames = (field: EditorField) =>
    [field.rule?.sourceFieldKey, ...graph.dependentsOf(field.key).map(({ key }) => key)]
      .map((key) => (key === undefined ? "" : (namesRef.current.get(key) ?? "")))
      .join("\u0000");

  const creatableOptions = useMemo(
    () => allTypeOptions.filter(({ value }) => (CREATABLE_TYPES as ReadonlyArray<string>).includes(value)),
    [allTypeOptions],
  );

  return (
    <Stack spacing={2} sx={{ my: 2 }}>
      <Typography variant="h5" component="h2">
        {t("formFieldEditor.heading")}
      </Typography>
      {status !== null && (
        <Alert severity="error" role="alert" aria-labelledby={statusId} onClose={() => setStatus(null)}>
          <AlertTitle id={statusId}>
            {status.kind === "problems"
              ? t("formFieldEditor.problemsTitle")
              : status.kind === "conflict"
                ? t("formFieldEditor.conflictTitle")
                : t("formFieldEditor.errorTitle")}
          </AlertTitle>
          {status.kind === "conflict" && (
            <Stack spacing={1} sx={{ alignItems: "flex-start" }}>
              <Typography variant="body2">{t("formFieldEditor.conflict")}</Typography>
              <Button size="small" variant="outlined" color="inherit" onClick={() => window.location.reload()}>
                {t("formFieldEditor.reload")}
              </Button>
            </Stack>
          )}
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
      {fields.length > 0 && editable && <DisplayConditionsNote />}
      {fields.length === 0 && (
        <Typography variant="body1" color="text.secondary">
          {t("formFieldEditor.noFields")}
        </Typography>
      )}
      <Stack spacing={2} ref={listRef}>
        {fields.map((field, index) => (
          <ElnFieldCard
            key={field.key}
            field={field}
            position={index}
            count={fields.length}
            ruleFields={ruleFields}
            shownNames={shownNames(field)}
            checked={checked[index]}
            editable={editable}
            allTypeOptions={allTypeOptions}
            creatableTypeOptions={creatableOptions}
            actions={actions}
          />
        ))}
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
