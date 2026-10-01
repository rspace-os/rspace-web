import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type BooleanFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "boolean" }>;
};

function BooleanFieldControl<TDocument>(props: BooleanFieldControlProps<TDocument>) {
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
