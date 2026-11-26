import { Temporal } from "@js-temporal/polyfill";
import { instantToWire, parseInstant } from "@/modules/common/time/instant";
import { Input } from "@/modules/common/ui/input";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import { emptyNullableValue } from "../fieldValues";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

function toInputValue(value: unknown): string {
  if (typeof value !== "string" || value === "") return "";
  const instant = parseInstant(value);
  if (instant === null) return value.slice(0, 16);
  return instant.toZonedDateTimeISO(Temporal.Now.timeZoneId()).toPlainDateTime().toString({ smallestUnit: "minute" });
}

function fromInputValue(value: string, nullable: boolean | undefined): string | null | undefined {
  if (value === "") return emptyNullableValue(nullable);
  try {
    const local = Temporal.PlainDateTime.from(value);
    return instantToWire(
      local.toZonedDateTime(Temporal.Now.timeZoneId(), { disambiguation: "compatible" }).toInstant(),
    );
  } catch {
    return value;
  }
}

function DateTimeFieldControl<TDocument>(props: ControlProps<TDocument, "dateTime">) {
  const { fieldApi, fieldConfig } = props;
  return (
    <Input
      {...controlAttributes(props)}
      className="rounded-sm"
      type="datetime-local"
      value={toInputValue(fieldApi.input)}
      readOnly={fieldConfig.readOnly}
      ref={fieldApi.props.ref}
      onFocus={fieldApi.props.onFocus}
      onBlur={fieldApi.props.onBlur}
      onChange={(event) => fieldApi.onChange(fromInputValue(event.currentTarget.value, fieldConfig.nullable))}
    />
  );
}

export function DateTimeField<TDocument extends Record<string, unknown>>(
  props: FieldRendererProps<TDocument, "dateTime">,
) {
  return (
    <FormField {...props}>
      {(controlProps) => <DateTimeFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
