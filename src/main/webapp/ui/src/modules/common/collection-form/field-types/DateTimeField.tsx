import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Input } from "@/modules/common/ui/input";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type DateTimeFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "dateTime" }>;
};

function toInputValue(value: unknown): string {
  if (typeof value !== "string" || value === "") return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16);
  const part = (number: number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(
    date.getMinutes(),
  )}`;
}

function fromInputValue(value: string, nullable: boolean | undefined): string | null | undefined {
  if (value === "") return nullable ? null : undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toISOString();
}

function DateTimeFieldControl<TDocument>(props: DateTimeFieldControlProps<TDocument>) {
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
