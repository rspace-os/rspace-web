import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Input } from "@/modules/common/ui/input";
import { Textarea } from "@/modules/common/ui/textarea";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type TextFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "text" }>;
};

function TextFieldControl<TDocument>(props: TextFieldControlProps<TDocument>) {
  const { fieldApi, fieldConfig } = props;
  const value = typeof fieldApi.input === "string" ? fieldApi.input : "";
  const onChange = (nextValue: string) =>
    fieldApi.onChange(nextValue === "" && fieldConfig.nullable ? null : nextValue);
  const common = controlAttributes(props);

  if (fieldConfig.form && fieldConfig.form.widget === "time") {
    return (
      <Input
        {...common}
        className="rounded-sm"
        type="time"
        value={value}
        readOnly={fieldConfig.readOnly}
        ref={fieldApi.props.ref}
        onFocus={fieldApi.props.onFocus}
        onBlur={fieldApi.props.onBlur}
        onChange={(event) => onChange(event.currentTarget.value)}
      />
    );
  }

  return fieldConfig.form && fieldConfig.form.widget === "textarea" ? (
    <Textarea
      {...common}
      className="rounded-sm"
      value={value}
      maxLength={fieldConfig.maximumLength}
      readOnly={fieldConfig.readOnly}
      ref={fieldApi.props.ref}
      onFocus={fieldApi.props.onFocus}
      onBlur={fieldApi.props.onBlur}
      onChange={(event) => onChange(event.currentTarget.value)}
    />
  ) : (
    <Input
      {...common}
      className="rounded-sm"
      type="text"
      value={value}
      maxLength={fieldConfig.maximumLength}
      readOnly={fieldConfig.readOnly}
      ref={fieldApi.props.ref}
      onFocus={fieldApi.props.onFocus}
      onBlur={fieldApi.props.onBlur}
      onChange={(event) => onChange(event.currentTarget.value)}
    />
  );
}

export function TextField<TDocument extends Record<string, unknown>>(props: FieldRendererProps<TDocument, "text">) {
  return (
    <FormField {...props}>
      {(controlProps) => <TextFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
