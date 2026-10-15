import { Input } from "@/modules/common/ui/input";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import { emptyNullableValue } from "../fieldValues";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

function NumberFieldControl<TDocument>(props: ControlProps<TDocument, "number">) {
  const { fieldApi, fieldConfig } = props;
  return (
    <Input
      {...controlAttributes(props)}
      className="rounded-sm"
      type="number"
      value={typeof fieldApi.input === "number" ? fieldApi.input : ""}
      readOnly={fieldConfig.readOnly}
      ref={fieldApi.props.ref}
      onFocus={fieldApi.props.onFocus}
      onBlur={fieldApi.props.onBlur}
      onChange={(event) =>
        fieldApi.onChange(
          event.currentTarget.value === ""
            ? emptyNullableValue(fieldConfig.nullable)
            : event.currentTarget.valueAsNumber,
        )
      }
    />
  );
}

export function NumberField<TDocument extends Record<string, unknown>>(props: FieldRendererProps<TDocument, "number">) {
  return (
    <FormField {...props}>
      {(controlProps) => <NumberFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
