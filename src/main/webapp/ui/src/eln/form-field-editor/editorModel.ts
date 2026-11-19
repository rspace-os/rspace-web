/**
 * The ELN form field editor's in-memory model: the fields being edited, how
 * they come from and go back to the server's legacy field DTOs, client checks,
 * and the edits that keep display rules consistent. Everything here is pure;
 * the components hold the state.
 */
import { format as formatDate, isValid, parse as parseDate } from "date-fns";
import { uniq } from "es-toolkit";
import * as v from "valibot";
import {
  type FieldDisplayRule,
  FieldDisplayRuleSchema,
  type SourceKind,
} from "@/modules/common/field-rules/displayRule";
import { parseElnChoiceValues } from "@/modules/common/field-rules/elnChoiceValues";
import { newFieldKey } from "@/modules/common/field-rules/newFieldKey";
import { isBlankOption, withoutOptions } from "@/modules/common/field-rules/ruleOptions";
import { type ElnFieldType, type FormFieldEditorItem, type FormFieldSaveItem, SourceSchemas } from "./formFieldsApi";

/** The types an author can add; Uri, Reference and Attachment fields come only from the forms API. */
export const CREATABLE_TYPES = ["Number", "String", "Text", "Radio", "Choice", "Date", "Time"] as const;
export type CreatableType = (typeof CREATABLE_TYPES)[number];

/** The date formats the legacy field editor offered, as Java `SimpleDateFormat` patterns. */
export const DATE_FORMATS = [
  "dd/MM/yyyy",
  "dd MM yyyy",
  "dd-MM-yyyy",
  "dd MMM yyyy",
  "yyyy/MM/dd",
  "yyyy MM dd",
  "yyyy-MM-dd",
  "yyyy MMM dd",
] as const;
export const TIME_FORMATS = ["hh:mm a", "HH:mm"] as const;

/** `AbstractFormFieldDTO.MAX_NAME_LENGTH` and `FieldUtils.MAX_OPTIONS_LENGTH`. */
export const NAME_MAX_LENGTH = 50;
const OPTIONS_MAX_LENGTH = 1000;

/** ISO formats of the browser's date and time inputs, which the model stores. */
const ISO_DATE = "yyyy-MM-dd";
const ISO_TIME = "HH:mm";

type FieldBase = {
  /** The field key; also the field's identity in the editor, so it is set when the field is added. */
  readonly key: string;
  /** Null until the field is saved. */
  readonly id: number | null;
  readonly name: string;
  readonly mandatory: boolean;
  readonly rule: FieldDisplayRule | null;
  /** False for fields copied from the published version into a draft. */
  readonly canDelete: boolean;
};

export type BoundName = "min" | "max" | "defaultValue";

type Bounded = {
  /** Empty when unset; dates as `yyyy-MM-dd` and times as `HH:mm`, whatever the display format. */
  readonly min: string;
  readonly max: string;
  readonly defaultValue: string;
  /**
   * Stored values the editor could not read (another locale's month names, or
   * a format from an archive import). They are sent back unchanged until the
   * author enters a new value, so a save never clears them silently.
   */
  readonly unread: Readonly<Partial<Record<BoundName, string>>>;
};

/**
 * A list of options exactly as the server stored it, with what the editor read
 * from it. Stored data may be in a form the editor would not write (an empty
 * option, or an "=" inside one), so the stored text goes back unchanged until
 * the author changes that list.
 */
type StoredList = { readonly text: string; readonly values: ReadonlyArray<string> };

export type FieldSettings =
  | {
      readonly type: "Number";
      readonly decimalPlaces: string;
      readonly min: string;
      readonly max: string;
      readonly defaultValue: string;
    }
  | { readonly type: "String"; readonly password: boolean; readonly defaultValue: string }
  | { readonly type: "Text"; readonly defaultValue: string }
  | {
      readonly type: "Radio";
      readonly options: ReadonlyArray<string>;
      readonly defaultValue: string;
      readonly showAsPickList: boolean;
      readonly sortAlphabetic: boolean;
      readonly stored?: { readonly options: StoredList };
    }
  | {
      readonly type: "Choice";
      readonly options: ReadonlyArray<string>;
      readonly defaults: ReadonlyArray<string>;
      readonly multiple: boolean;
      readonly stored?: { readonly options: StoredList; readonly defaults: StoredList };
    }
  | ({ readonly type: "Date"; readonly format: string } & Bounded)
  | ({ readonly type: "Time"; readonly format: string } & Bounded)
  | { readonly type: "Uri" | "Reference" | "Attachment" };

export type EditorField = FieldBase & FieldSettings;
export type FieldOfType<T extends ElnFieldType> = Extract<EditorField, { type: T }>;

export function hasOptions(field: EditorField): field is FieldOfType<"Radio" | "Choice"> {
  return field.type === "Radio" || field.type === "Choice";
}

/**
 * What a rule reading the field compares, as the server's `ElnFieldRules.sourceKind`
 * decides it: a password String is never read, so its value cannot appear in a rule.
 */
export function elnSourceKind(field: FieldSettings): SourceKind {
  switch (field.type) {
    case "Radio":
    case "Choice":
      return "OPTIONS";
    case "Number":
      return "NUMBER";
    case "String":
      return field.password ? "NONE" : "TEXT";
    default:
      return "NONE";
  }
}

export const canBeSource = (field: FieldSettings): boolean => elnSourceKind(field) !== "NONE";

function defaultSettings(type: CreatableType, previous?: EditorField): FieldSettings {
  const options = previous && hasOptions(previous) ? previous.options : [];
  switch (type) {
    case "Number":
      return { type, decimalPlaces: "", min: "", max: "", defaultValue: "" };
    case "String":
      return { type, password: false, defaultValue: "" };
    case "Text":
      return { type, defaultValue: "" };
    case "Radio":
      return { type, options, defaultValue: "", showAsPickList: false, sortAlphabetic: false };
    case "Choice":
      return { type, options, defaults: [], multiple: false };
    case "Date":
      return { type, format: "yyyy-MM-dd", min: "", max: "", defaultValue: "", unread: {} };
    case "Time":
      return { type, format: "HH:mm", min: "", max: "", defaultValue: "", unread: {} };
  }
}

/** A new, unsaved field. Its key is made now, so rules can use it before the first save. */
export function newField(type: CreatableType): EditorField {
  return {
    key: newFieldKey(),
    id: null,
    name: "",
    mandatory: false,
    rule: null,
    canDelete: true,
    ...defaultSettings(type),
  };
}

/* Dates and times: the server writes and reads them in the field's display format. */

/** The value in another format, or null if it cannot be read (date-fns throws on tokens it does not know). */
function convert(value: string, from: string, to: string): string | null {
  if (value === "") return "";
  try {
    const parsed = parseDate(value, from, new Date(2000, 0, 1));
    return isValid(parsed) ? formatDate(parsed, to) : null;
  } catch {
    return null;
  }
}

const BOUNDS: ReadonlyArray<BoundName> = ["min", "max", "defaultValue"];

/** Server bounds in the editor's ISO form, keeping any the editor cannot read. */
function readBounds(stored: Record<BoundName, string>, format: string, iso: string): Bounded {
  const bounded = { min: "", max: "", defaultValue: "", unread: {} as Partial<Record<BoundName, string>> };
  for (const bound of BOUNDS) {
    const converted = convert(stored[bound], format, iso);
    if (converted === null) bounded.unread[bound] = stored[bound];
    else bounded[bound] = converted;
  }
  return bounded;
}

/** Bounds in the field's format; an unread value goes back as it came while its input is empty. */
function writeBounds(
  field: Bounded & { format: string },
  iso: string,
): { minValue: string; maxValue: string; defaultValue: string } {
  const write = (bound: BoundName) =>
    (field[bound] === "" ? field.unread[bound] : convert(field[bound], iso, field.format)) ?? field.unread[bound] ?? "";
  return { minValue: write("min"), maxValue: write("max"), defaultValue: write("defaultValue") };
}

function wireRule(rule: unknown): FieldDisplayRule | null {
  if (rule === null || rule === undefined) return null;
  const { values, ...rest } = rule as { values?: unknown };
  const parsed = v.safeParse(FieldDisplayRuleSchema, values === null ? rest : rule);
  return parsed.success ? parsed.output : null;
}

/** A saved field, from the server's item. */
export function fromServer(item: FormFieldEditorItem): EditorField {
  const base = {
    key: item.fieldKey,
    id: item.id,
    rule: wireRule(item.displayRule),
    canDelete: item.canDelete,
  };
  switch (item.type) {
    case "Number": {
      const s = v.parse(SourceSchemas.Number, item.source);
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "Number",
        decimalPlaces: s.decimalPlaces,
        min: s.minNumberValue,
        max: s.maxNumberValue,
        defaultValue: s.defaultNumberValue,
      };
    }
    case "String": {
      const s = v.parse(SourceSchemas.String, item.source);
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "String",
        password: s.ifPassword === "yes",
        defaultValue: s.defaultStringValue,
      };
    }
    case "Text": {
      const s = v.parse(SourceSchemas.Text, item.source);
      return { ...base, name: s.name, mandatory: s.mandatory, type: "Text", defaultValue: s.defaultValue };
    }
    case "Radio": {
      const s = v.parse(SourceSchemas.Radio, item.source);
      const options = parseElnChoiceValues(s.radioValues);
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "Radio",
        options,
        defaultValue: s.radioSelected,
        showAsPickList: s.showAsPickList,
        sortAlphabetic: s.sortAlphabetic,
        stored: { options: { text: s.radioValues, values: options } },
      };
    }
    case "Choice": {
      const s = v.parse(SourceSchemas.Choice, item.source);
      const options = parseElnChoiceValues(s.choiceValues);
      const defaults = parseElnChoiceValues(s.selectedValues);
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "Choice",
        options,
        defaults,
        multiple: s.multipleChoice === "yes",
        stored: {
          options: { text: s.choiceValues, values: options },
          defaults: { text: s.selectedValues, values: defaults },
        },
      };
    }
    case "Date": {
      const s = v.parse(SourceSchemas.Date, item.source);
      const format = s.dateFormat || "yyyy-MM-dd";
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "Date",
        format,
        ...readBounds({ min: s.minValue, max: s.maxValue, defaultValue: s.defaultValue }, format, ISO_DATE),
      };
    }
    case "Time": {
      const s = v.parse(SourceSchemas.Time, item.source);
      const format = s.timeFormat || "HH:mm";
      return {
        ...base,
        name: s.name,
        mandatory: s.mandatory,
        type: "Time",
        format,
        ...readBounds({ min: s.minValue, max: s.maxValue, defaultValue: s.defaultValue }, format, ISO_TIME),
      };
    }
    case "Uri":
    case "Reference":
    case "Attachment": {
      const s = v.parse(SourceSchemas[item.type], item.source);
      return { ...base, name: s.name, mandatory: s.mandatory, type: item.type };
    }
  }
}

/** Options in the stored `name=value&name=value` format; no URL encoding, as the server reads them raw. */
const joinOptions = (name: string, options: ReadonlyArray<string>) =>
  options.map((option) => `${name}=${option}`).join("&");

const sameValues = (a: ReadonlyArray<string>, b: ReadonlyArray<string>) =>
  a.length === b.length && a.every((value, i) => value === b[i]);

const isStored = (values: ReadonlyArray<string>, stored: StoredList | undefined): stored is StoredList =>
  stored !== undefined && sameValues(stored.values, values);

/** The stored text while the list is as it was read, so untouched legacy data is not rewritten. */
const writeOptions = (name: string, options: ReadonlyArray<string>, stored: StoredList | undefined) =>
  isStored(options, stored) ? stored.text : joinOptions(name, options);

/**
 * A default as the new options offer it, or null if they do not. A stored
 * default is read the way its options were, so `Y=1` beside the stored option
 * `fieldRadios=Y=1`, which reads as `Y`, is `Y`.
 */
function offeredDefault(options: ReadonlyArray<string>, value: string): string | null {
  if (options.includes(value)) return value;
  const parsed = parseElnChoiceValues(`default=${value}`)[0];
  return parsed !== undefined && options.includes(parsed) ? parsed : null;
}

/**
 * Defaults go back as stored only beside options that go back as stored. Once
 * the options are written anew, so are the defaults, read as the options are
 * and kept only if the new options offer them: stored default text may name
 * values its options do not.
 */
function writeDefaults<D>(
  field: { options: ReadonlyArray<string>; stored?: { options: StoredList } },
  asStored: () => D,
  offered: (options: ReadonlyArray<string>) => D,
): D {
  return isStored(field.options, field.stored?.options) ? asStored() : offered(field.options);
}

function source(field: EditorField): Record<string, string | boolean> {
  const common = { name: field.name, mandatory: field.mandatory };
  switch (field.type) {
    case "Number":
      return {
        ...common,
        decimalPlaces: field.decimalPlaces.trim(),
        minNumberValue: field.min.trim(),
        maxNumberValue: field.max.trim(),
        defaultNumberValue: field.defaultValue.trim(),
      };
    case "String":
      return { ...common, ifPassword: field.password ? "yes" : "no", defaultStringValue: field.defaultValue };
    case "Text":
      return { ...common, defaultValue: field.defaultValue };
    case "Radio":
      return {
        ...common,
        fieldName: field.name,
        radioValues: writeOptions("fieldRadios", field.options, field.stored?.options),
        radioSelected: writeDefaults(
          field,
          () => field.defaultValue,
          (options) => offeredDefault(options, field.defaultValue) ?? "",
        ),
        showAsPickList: field.showAsPickList,
        sortAlphabetic: field.sortAlphabetic,
      };
    case "Choice":
      return {
        ...common,
        fieldName: field.name,
        choiceValues: writeOptions("fieldChoices", field.options, field.stored?.options),
        multipleChoice: field.multiple ? "yes" : "no",
        selectedValues: writeDefaults(
          field,
          () => writeOptions("fieldSelectedChoices", field.defaults, field.stored?.defaults),
          (options) =>
            joinOptions(
              "fieldSelectedChoices",
              uniq(field.defaults.flatMap((value) => offeredDefault(options, value) ?? [])),
            ),
        ),
      };
    case "Date":
      return {
        ...common,
        dateFormat: field.format,
        ...writeBounds(field, ISO_DATE),
      };
    case "Time":
      return {
        ...common,
        timeFormat: field.format,
        ...writeBounds(field, ISO_TIME),
      };
    case "Uri":
    case "Reference":
    case "Attachment":
      return common;
  }
}

/** The save item for a field. The rule is always sent, so a cleared rule reaches the server as null. */
export function toSaveItem(field: EditorField): FormFieldSaveItem {
  return {
    id: field.id,
    type: field.type,
    fieldKey: field.key,
    displayRule: field.rule
      ? {
          sourceFieldKey: field.rule.sourceFieldKey,
          operator: field.rule.operator,
          ...(field.rule.values.length > 0 ? { values: field.rule.values } : {}),
        }
      : null,
    source: source(field),
  };
}

/** A field's save item as last saved: its text, and its options as sent. */
type SavedItem = { readonly text: string; readonly options: unknown };

const optionsOf = (item: FormFieldSaveItem): unknown => item.source.radioValues ?? item.source.choiceValues;

/** Each field's save item by key, as last saved, to tell which fields the author has changed since. */
export const savedItems = (fields: ReadonlyArray<EditorField>): ReadonlyMap<string, SavedItem> =>
  new Map(
    fields.map((field) => {
      const item = toSaveItem(field);
      return [field.key, { text: JSON.stringify(item), options: optionsOf(item) }];
    }),
  );

/**
 * Which client checks apply to a field, as on the server: all of them to a new
 * field; none to a saved field the author has not changed, which may hold data
 * written through the forms API that the editor would refuse; and the option
 * checks only once its options change, so that stored options the editor would
 * refuse do not block other changes to the field.
 */
export type FieldChecks = "all" | "exceptOptions" | "none";

export function checksFor(field: EditorField, saved: ReadonlyMap<string, SavedItem>): FieldChecks {
  const before = field.id === null ? undefined : saved.get(field.key);
  if (!before) return "all";
  const item = toSaveItem(field);
  if (JSON.stringify(item) === before.text) return "none";
  return optionsOf(item) === before.options ? "exceptOptions" : "all";
}

/** The problems the checks that apply find. */
export function problemsFor(field: EditorField, checks: FieldChecks): FieldProblems {
  if (checks === "none") return {};
  const { options, ...problems } = fieldProblems(field);
  return checks === "all" && options ? { ...problems, options } : problems;
}

/**
 * The server's ids for fields saved while the author kept editing, and the
 * option and default text they were saved with, so that taking an edit back
 * sends what the server now holds; the newer edits stay. A default the author
 * has not changed since the save was sent becomes the default the server
 * saved, which may differ: writing new options can rewrite or clear it.
 *
 * @param sent the fields as the save sent them
 */
export function adoptSavedIds(
  current: ReadonlyArray<EditorField>,
  saved: ReadonlyArray<EditorField>,
  sent: ReadonlyArray<EditorField>,
): Array<EditorField> {
  return current.map((field): EditorField => {
    const match = saved.find(({ key }) => key === field.key);
    if (!match) return field;
    const before = sent.find(({ key }) => key === field.key);
    const ids = { id: match.id, canDelete: match.canDelete };
    if (field.type === "Radio" && match.type === "Radio") {
      const unchanged = before?.type === "Radio" && before.defaultValue === field.defaultValue;
      return {
        ...field,
        ...ids,
        stored: match.stored,
        defaultValue: unchanged ? match.defaultValue : field.defaultValue,
      };
    }
    if (field.type === "Choice" && match.type === "Choice") {
      const unchanged = before?.type === "Choice" && sameValues(before.defaults, field.defaults);
      return { ...field, ...ids, stored: match.stored, defaults: unchanged ? match.defaults : field.defaults };
    }
    return { ...field, ...ids };
  });
}

/** What a save would send; two equal signatures mean nothing to save. */
export const saveSignature = (fields: ReadonlyArray<EditorField>): string => JSON.stringify(fields.map(toSaveItem));

/**
 * Whether two field lists hold the same fields, whatever their ids: what a
 * save sent, with no ids for new fields, and what the server then holds.
 */
export const sameFields = (a: ReadonlyArray<EditorField>, b: ReadonlyArray<EditorField>): boolean => {
  const withoutIds = (fields: ReadonlyArray<EditorField>) =>
    JSON.stringify(fields.map((field) => ({ ...toSaveItem(field), id: null })));
  return withoutIds(a) === withoutIds(b);
};

/* Client checks, before anything is sent. The server checks everything again. */

export type FieldProblem =
  | "nameRequired"
  | "nameTooLong"
  | "optionsRequired"
  | "optionsInvalid"
  | "optionCharacters"
  | "optionsTooLong"
  | "notANumber"
  | "decimalPlaces"
  | "minAboveMax"
  | "belowMin"
  | "aboveMax";

export type FieldProblems = Partial<
  Record<"name" | "options" | "decimalPlaces" | "min" | "max" | "defaultValue", FieldProblem>
>;

const isNumber = (value: string) => value.trim() !== "" && Number.isFinite(Number(value));

function boundProblems(
  min: string,
  max: string,
  value: string,
  compare: (a: string, b: string) => number,
): FieldProblems {
  if (min !== "" && max !== "" && compare(min, max) > 0) return { min: "minAboveMax" };
  if (value !== "" && min !== "" && compare(value, min) < 0) return { defaultValue: "belowMin" };
  if (value !== "" && max !== "" && compare(value, max) > 0) return { defaultValue: "aboveMax" };
  return {};
}

export function fieldProblems(field: EditorField): FieldProblems {
  const problems: FieldProblems = {};
  if (field.name.trim() === "") problems.name = "nameRequired";
  else if (field.name.length > NAME_MAX_LENGTH) problems.name = "nameTooLong";

  switch (field.type) {
    case "Radio":
    case "Choice": {
      const { options } = field;
      if (options.length === 0) problems.options = "optionsRequired";
      else if (options.some(isBlankOption) || uniq(options).length !== options.length)
        problems.options = "optionsInvalid";
      else if (options.some((option) => /[&=]/.test(option))) problems.options = "optionCharacters";
      else if (
        joinOptions(field.type === "Radio" ? "fieldRadios" : "fieldChoices", options).length > OPTIONS_MAX_LENGTH
      )
        problems.options = "optionsTooLong";
      break;
    }
    case "Number": {
      const decimals = field.decimalPlaces.trim();
      if (decimals !== "" && !/^\d+$/.test(decimals)) problems.decimalPlaces = "decimalPlaces";
      else if (decimals !== "" && Number(decimals) > 127) problems.decimalPlaces = "decimalPlaces";
      for (const bound of ["min", "max", "defaultValue"] as const) {
        if (field[bound].trim() !== "" && !isNumber(field[bound])) problems[bound] = "notANumber";
      }
      if (!problems.min && !problems.max && !problems.defaultValue) {
        Object.assign(
          problems,
          boundProblems(field.min.trim(), field.max.trim(), field.defaultValue.trim(), (a, b) => Number(a) - Number(b)),
        );
      }
      break;
    }
    case "Date":
    case "Time":
      // ISO dates and 24-hour times compare correctly as strings
      Object.assign(
        problems,
        boundProblems(field.min, field.max, field.defaultValue, (a, b) => a.localeCompare(b)),
      );
      break;
    default:
      break;
  }
  return problems;
}

/* Edits that keep display rules consistent with the fields they read. */

export const dependentsOf = (fields: ReadonlyArray<EditorField>, key: string): Array<EditorField> =>
  fields.filter((field) => field.rule?.sourceFieldKey === key);

export const fieldsUsingOption = (
  fields: ReadonlyArray<EditorField>,
  key: string,
  option: string,
): Array<EditorField> => dependentsOf(fields, key).filter((field) => field.rule?.values.includes(option));

export function updateField(
  fields: ReadonlyArray<EditorField>,
  key: string,
  update: (field: EditorField) => EditorField,
): Array<EditorField> {
  return fields.map((field) => (field.key === key ? update(field) : field));
}

/** Removes a field, and the rules that read it. */
export function deleteField(fields: ReadonlyArray<EditorField>, key: string): Array<EditorField> {
  return fields
    .filter((field) => field.key !== key)
    .map((field) => (field.rule?.sourceFieldKey === key ? { ...field, rule: null } : field));
}

/** The field with another type's default settings, keeping what every type has and any options. */
function retyped(field: EditorField, type: CreatableType): EditorField {
  const { key, id, name, mandatory, rule, canDelete } = field;
  return { key, id, name, mandatory, rule, canDelete, ...defaultSettings(type, field) };
}

/** Whether changing the field to `type` changes what a rule reading it compares, which its dependents' rules cannot survive. */
export const changesSourceKind = (field: EditorField, type: CreatableType): boolean =>
  elnSourceKind(retyped(field, type)) !== elnSourceKind(field);

/** Changes an unsaved field's type; if what a rule reading it compares changes, the rules that read it go. */
export function changeType(fields: ReadonlyArray<EditorField>, key: string, type: CreatableType): Array<EditorField> {
  const target = fields.find((field) => field.key === key);
  if (!target) return [...fields];
  const clearsRules = changesSourceKind(target, type);
  return fields.map((field) => {
    if (field.key === key) return retyped(field, type);
    if (clearsRules && field.rule?.sourceFieldKey === key) return { ...field, rule: null };
    return field;
  });
}

function withOptionsChange(
  field: FieldOfType<"Radio" | "Choice">,
  options: ReadonlyArray<string>,
  mapDefault: (value: string) => string | null,
): EditorField {
  if (field.type === "Radio") {
    return { ...field, options, defaultValue: mapDefault(field.defaultValue) ?? "" };
  }
  return {
    ...field,
    options,
    defaults: uniq(field.defaults.flatMap((value) => mapDefault(value) ?? [])),
  };
}

/** Renames an option. A default follows it by value; rules never do, as the editor locks options they use. */
export function renameOption(
  fields: ReadonlyArray<EditorField>,
  key: string,
  index: number,
  value: string,
): Array<EditorField> {
  return updateField(fields, key, (field) => {
    if (!hasOptions(field)) return field;
    const old = field.options[index];
    const options = field.options.map((option, i) => (i === index ? value : option));
    // a blank option is never a default, so an unset default must not follow one being typed into
    const follows = !isBlankOption(old) && !options.includes(old);
    // read as the options before the rename read it, so a stored `Y=1` that is option `Y` follows it
    const isOld = (selected: string) => offeredDefault(field.options, selected) === old;
    return withOptionsChange(field, options, (selected) => (follows && isOld(selected) ? value : selected));
  });
}

/** Removes an option; if no other option has the same value, rules and defaults stop using it. */
export function removeOption(fields: ReadonlyArray<EditorField>, key: string, index: number): Array<EditorField> {
  const owner = fields.find((field) => field.key === key);
  if (!owner || !hasOptions(owner)) return [...fields];
  const removed = owner.options[index];
  const options = owner.options.filter((_, i) => i !== index);
  if (options.includes(removed)) return updateField(fields, key, () => ({ ...owner, options }));
  return fields.map((field) => {
    if (field.key === key)
      return withOptionsChange(owner, options, (selected) => (selected === removed ? null : selected));
    if (field.rule?.sourceFieldKey === key) return { ...field, rule: withoutOptions(field.rule, [removed]) };
    return field;
  });
}

export function moveField(fields: ReadonlyArray<EditorField>, key: string, position: number): Array<EditorField> {
  const field = fields.find((candidate) => candidate.key === key);
  if (!field) return [...fields];
  const rest = fields.filter((candidate) => candidate.key !== key);
  return [...rest.slice(0, position), field, ...rest.slice(position)];
}
