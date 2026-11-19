/**
 * The form field editor's server contract: `GET` and `POST
 * /workspace/editor/form/ajax/fields?formId=` (RSFormController). Each item is
 * a field as the legacy field DTOs describe it, plus its id, key, display rule
 * and whether it can be deleted. The list comes with its version, the revision
 * that every save of the list advances; a save sends it back, so that it
 * cannot overwrite a newer save from elsewhere.
 */
import * as v from "valibot";
import axios, { isAxiosError } from "@/common/axios";

export const ELN_FIELD_TYPES = [
  "Number",
  "String",
  "Text",
  "Radio",
  "Choice",
  "Date",
  "Time",
  "Uri",
  "Reference",
  "Attachment",
] as const;

export type ElnFieldType = (typeof ELN_FIELD_TYPES)[number];

const optionalString = v.nullish(v.string(), "");

const CommonSourceSchema = {
  name: optionalString,
  mandatory: v.nullish(v.boolean(), false),
};

/** The legacy DTO of each type, as `LegacyFormFieldDTOs.fromFieldForm` writes it. */
export const SourceSchemas = {
  Number: v.object({
    ...CommonSourceSchema,
    minNumberValue: optionalString,
    maxNumberValue: optionalString,
    decimalPlaces: optionalString,
    defaultNumberValue: optionalString,
  }),
  String: v.object({ ...CommonSourceSchema, ifPassword: optionalString, defaultStringValue: optionalString }),
  Text: v.object({ ...CommonSourceSchema, defaultValue: optionalString }),
  Radio: v.object({
    ...CommonSourceSchema,
    radioValues: optionalString,
    radioSelected: optionalString,
    showAsPickList: v.nullish(v.boolean(), false),
    sortAlphabetic: v.nullish(v.boolean(), false),
  }),
  Choice: v.object({
    ...CommonSourceSchema,
    choiceValues: optionalString,
    multipleChoice: optionalString,
    selectedValues: optionalString,
  }),
  Date: v.object({
    ...CommonSourceSchema,
    defaultValue: optionalString,
    minValue: optionalString,
    maxValue: optionalString,
    dateFormat: optionalString,
  }),
  Time: v.object({
    ...CommonSourceSchema,
    defaultValue: optionalString,
    minValue: optionalString,
    maxValue: optionalString,
    timeFormat: optionalString,
  }),
  Uri: v.object(CommonSourceSchema),
  Reference: v.object(CommonSourceSchema),
  Attachment: v.object(CommonSourceSchema),
} satisfies Record<ElnFieldType, v.GenericSchema>;

export type ElnFieldSource<T extends ElnFieldType> = v.InferOutput<(typeof SourceSchemas)[T]>;

const RuleWireSchema = v.nullish(
  v.object({
    sourceFieldKey: v.string(),
    operator: v.string(),
    values: v.nullish(v.array(v.string())),
  }),
  null,
);

const ItemSchema = v.object({
  id: v.number(),
  type: v.picklist(ELN_FIELD_TYPES),
  fieldKey: v.string(),
  displayRule: RuleWireSchema,
  canDelete: v.boolean(),
  /** Parsed with the type's schema in `SourceSchemas` when the item becomes an editor field. */
  source: v.record(v.string(), v.unknown()),
});

/** A saved field as the server returns it. */
export type FormFieldEditorItem = v.InferOutput<typeof ItemSchema>;

/** One field of a save: a saved field has its id, a new one has none. */
export type FormFieldSaveItem = {
  id: number | null;
  type: ElnFieldType;
  fieldKey: string;
  /** Always sent: null clears a saved field's rule. */
  displayRule: { sourceFieldKey: string; operator: string; values?: ReadonlyArray<string> } | null;
  source: Record<string, string | boolean>;
};

const FieldListSchema = v.object({ fields: v.array(ItemSchema), version: v.number() });

/** The form's saved fields, in order, and the version of that list. */
export type FormFieldList = v.InferOutput<typeof FieldListSchema>;

const ResponseSchema = v.object({
  data: v.nullable(FieldListSchema),
  errorMsg: v.nullish(v.object({ errorMessages: v.array(v.string()) }), null),
});

/** The server rejected a save; each message says why. */
export class FormFieldsSaveError extends Error {
  readonly messages: ReadonlyArray<string>;

  constructor(messages: ReadonlyArray<string>) {
    super(messages.join("\n"));
    this.name = "FormFieldsSaveError";
    this.messages = messages;
  }
}

/** The fields changed elsewhere, for example in another tab, after this editor read them; nothing was saved. */
export class FormFieldsConflictError extends FormFieldsSaveError {
  constructor(messages: ReadonlyArray<string>) {
    super(messages);
    this.name = "FormFieldsConflictError";
  }
}

function fieldListFrom(body: unknown): FormFieldList {
  const response = v.parse(ResponseSchema, body);
  if (response.data === null) throw new FormFieldsSaveError(response.errorMsg?.errorMessages ?? []);
  return response.data;
}

const fieldsUrl = (formId: number) => `/workspace/editor/form/ajax/fields?formId=${formId}`;

export async function fetchFormFields(formId: number): Promise<FormFieldList> {
  const { data } = await axios.get<unknown>(fieldsUrl(formId));
  return fieldListFrom(data);
}

/** The messages of a rejection, when the response carries them. */
function rejectionMessages(body: unknown): ReadonlyArray<string> {
  const parsed = v.safeParse(ResponseSchema, body);
  return parsed.success ? (parsed.output.errorMsg?.errorMessages ?? []) : [];
}

/**
 * Saves the full, ordered field list; the server applies every change in one
 * transaction and returns the saved fields with their new version.
 *
 * @param version the version of the field list the items were edited from
 * @throws {FormFieldsConflictError} when the fields changed after that version
 * @throws {FormFieldsSaveError} when the server rejects the save, for example
 *   because the user may not change the form
 */
export async function saveFormFields(
  formId: number,
  items: ReadonlyArray<FormFieldSaveItem>,
  version: number,
): Promise<FormFieldList> {
  try {
    const { data } = await axios.post<unknown>(fieldsUrl(formId), items, { params: { version } });
    return fieldListFrom(data);
  } catch (error) {
    if (!isAxiosError(error) || !error.response) throw error;
    const messages = rejectionMessages(error.response.data);
    if (error.response.status === 409) throw new FormFieldsConflictError(messages);
    if (messages.length > 0) throw new FormFieldsSaveError(messages);
    throw error;
  }
}
