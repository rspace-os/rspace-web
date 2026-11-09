import { format } from "date-fns";
import type { HTMLInputTypeAttribute } from "react";
import type * as v from "valibot";
import type { ResolvedFieldConfig, SelectOption } from "@/modules/common/collection/config";
import type { FieldType } from "@/modules/common/collection/fieldTypes";
import type { RuntimeFieldValueType } from "@/modules/common/collection/runtimeFields";
import { booleanScalar, dateTimeScalar, numberScalar } from "./schema";
import type { DisplayFormat } from "./utils/displayValue";

type ScalarFieldType = "text" | "number" | "boolean" | "dateTime" | "select" | "relationship";

type DraftScalar = string | number | boolean | Date;

export type ValueBehaviour = {
  /** Schema for a typed URL `where` scalar; absent keeps the string. */
  urlScalar?: v.GenericSchema<string, number | boolean | Date>;
  /** Draft text for a Date or string filter value; absent uses ISO text for a Date and `String` otherwise. */
  toDraft?: (value: Date | string) => string;
  /** Filter value from one draft entry, given the original value at the same position of an unchanged field. */
  fromDraft?: (draft: string, previous: DraftScalar | undefined) => DraftScalar;
  comparesAsInstant: boolean;
  displayFormat?: DisplayFormat;
  valueControl: "select" | "boolean" | "input";
  inputType: HTMLInputTypeAttribute;
  inputStep?: string;
  /** Whether a custom date or time type replaces the input type. */
  runtimeTypeSetsInput: boolean;
  prefersEquals: boolean;
};

const DATE_TIME_LOCAL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

const toLocalDateTime = (value: Date | string) => format(new Date(value), DATE_TIME_LOCAL_FORMAT);

function instantOf(value: DraftScalar | undefined): Date | null {
  if (value instanceof Date) return value;
  if (typeof value === "string") return new Date(value);
  return null;
}

const text: ValueBehaviour = {
  comparesAsInstant: false,
  valueControl: "input",
  inputType: "text",
  runtimeTypeSetsInput: true,
  prefersEquals: false,
};

const valueBehaviours: Readonly<Record<ScalarFieldType, ValueBehaviour>> = {
  text,
  number: {
    ...text,
    urlScalar: numberScalar,
    fromDraft: (draft) => Number(draft),
    displayFormat: "number",
    inputType: "number",
    runtimeTypeSetsInput: false,
  },
  boolean: { ...text, urlScalar: booleanScalar, fromDraft: (draft) => draft === "true", valueControl: "boolean" },
  dateTime: {
    ...text,
    urlScalar: dateTimeScalar,
    toDraft: toLocalDateTime,
    // A local clock value cannot distinguish the two instants in a DST overlap, so an unchanged draft keeps its original.
    fromDraft: (draft, previous) => {
      const instant = instantOf(previous);
      return instant && toLocalDateTime(instant) === draft ? instant : new Date(draft);
    },
    comparesAsInstant: true,
    displayFormat: "dateTime",
    inputType: "datetime-local",
    inputStep: "any",
  },
  select: { ...text, valueControl: "select", prefersEquals: true },
  relationship: { ...text, prefersEquals: true },
};

/** Custom (runtime) field value types that change how a value is entered and shown. */
const runtimeValueBehaviours: Readonly<
  Partial<Record<RuntimeFieldValueType, { inputType: "date" | "time"; displayFormat: "date" | "time" }>>
> = {
  date: { inputType: "date", displayFormat: "date" },
  time: { inputType: "time", displayFormat: "time" },
};

function isScalarFieldType(type: FieldType): type is ScalarFieldType {
  return Object.hasOwn(valueBehaviours, type);
}

export type ResolvedValueBehaviour = {
  behaviour: ValueBehaviour;
  inputType: HTMLInputTypeAttribute;
  displayFormat: DisplayFormat | undefined;
  /** A select field's configured options. */
  selectOptions: readonly SelectOption[] | undefined;
};

/** How a field's values are parsed, drafted, compared, entered and shown in a table list. */
export function valueBehaviourOf<TDocument>(field: ResolvedFieldConfig<TDocument> | undefined): ResolvedValueBehaviour {
  // The default also covers field types added after the scalar ones, which this module does not list.
  const behaviour = field && isScalarFieldType(field.type) ? valueBehaviours[field.type] : text;
  const runtimeType = field?.origin?.runtimeValueType;
  const runtime = runtimeType === undefined ? undefined : runtimeValueBehaviours[runtimeType];
  return {
    behaviour,
    inputType: behaviour.runtimeTypeSetsInput && runtime ? runtime.inputType : behaviour.inputType,
    displayFormat: behaviour.displayFormat ?? runtime?.displayFormat,
    selectOptions: field?.type === "select" ? field.options : undefined,
  };
}
