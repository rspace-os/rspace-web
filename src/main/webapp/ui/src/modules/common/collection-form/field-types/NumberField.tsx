import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Input } from "@/modules/common/ui/input";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type NumberFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "number" }>;
};

function NumberFieldControl<TDocument>(props: NumberFieldControlProps<TDocument>) {
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
            ? fieldConfig.nullable
              ? null
              : undefined
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
