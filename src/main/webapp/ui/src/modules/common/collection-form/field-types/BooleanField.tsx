import { Checkbox } from "@/modules/common/ui/checkbox";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

function BooleanFieldControl<TDocument>(props: ControlProps<TDocument, "boolean">) {
  const { disabled, fieldApi, fieldConfig } = props;
  return (
    <Checkbox
      {...controlAttributes(props)}
      className="rounded-sm"
      checked={fieldApi.input === true}
      disabled={disabled || fieldConfig.readOnly}
      inputRef={fieldApi.props.ref}
      onCheckedChange={(checked) => fieldApi.onChange(checked)}
    />
  );
}

export function BooleanField<TDocument extends Record<string, unknown>>(
  props: FieldRendererProps<TDocument, "boolean">,
) {
  return (
    <FormField {...props} orientation="horizontal">
      {(controlProps) => <BooleanFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
