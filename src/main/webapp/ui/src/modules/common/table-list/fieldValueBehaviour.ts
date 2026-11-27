import { Temporal } from "@js-temporal/polyfill";
import type { HTMLInputTypeAttribute } from "react";
import type * as v from "valibot";
import type { ResolvedFieldConfig, SelectOption, StructuredFieldType } from "@/modules/common/collection/config";
import type { FieldType } from "@/modules/common/collection/fieldTypes";
import type { RuntimeFieldValueType } from "@/modules/common/collection/runtimeFields";
import { parseInstant, truncateToMilliseconds } from "@/modules/common/time/instant";
import { booleanScalar, dateTimeScalar, numberScalar } from "./schema";
import type { FilterScalar } from "./types";
import type { DisplayFormat } from "./utils/displayValue";

type ScalarFieldType = Exclude<FieldType, StructuredFieldType>;

export type ValueBehaviour = {
  /** Schema for a typed URL `where` scalar; absent keeps the string. */
  urlScalar?: v.GenericSchema<string, number | boolean | Temporal.Instant>;
  /** Draft text for an instant or string filter value; absent uses ISO text for an instant and `String` otherwise. */
  toDraft?: (value: Temporal.Instant | string) => string;
  /** Filter value from one draft entry, given the original value at the same position of an unchanged field. */
  fromDraft?: (draft: string, previous: FilterScalar | undefined) => FilterScalar;
  comparesAsInstant: boolean;
  displayFormat?: DisplayFormat;
  valueControl: "select" | "boolean" | "input";
  inputType: HTMLInputTypeAttribute;
  inputStep?: string;
  /** Whether a custom date or time type replaces the input type. */
  runtimeTypeSetsInput: boolean;
  prefersEquals: boolean;
};

function instantOf(value: FilterScalar | undefined): Temporal.Instant | null {
  if (value instanceof Temporal.Instant) return value;
  const parsed = typeof value === "string" ? parseInstant(value) : null;
  return parsed && truncateToMilliseconds(parsed);
}

/** `yyyy-MM-ddTHH:mm:ss.SSS` in the browser's time zone; text that is not an instant is kept. */
function toLocalDateTime(value: Temporal.Instant | string): string {
  const instant = instantOf(value);
  if (instant === null) return String(value);
  return instant
    .toZonedDateTimeISO(Temporal.Now.timeZoneId())
    .toPlainDateTime()
    .toString({ fractionalSecondDigits: 3 });
}

/** The instant of an edited local draft; a skipped clock time moves forward and a repeated one takes the earlier instant. */
function fromLocalDateTime(draft: string): FilterScalar {
  try {
    const local = Temporal.PlainDateTime.from(draft);
    return truncateToMilliseconds(
      local.toZonedDateTime(Temporal.Now.timeZoneId(), { disambiguation: "compatible" }).toInstant(),
    );
  } catch {
    return draft;
  }
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
      return instant && toLocalDateTime(instant) === draft ? instant : fromLocalDateTime(draft);
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
