import { Input } from "@/modules/common/ui/input";
import { Textarea } from "@/modules/common/ui/textarea";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import { emptyNullableValue } from "../fieldValues";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

function TextFieldControl<TDocument>(props: ControlProps<TDocument, "text">) {
  const { fieldApi, fieldConfig } = props;
  const value = typeof fieldApi.input === "string" ? fieldApi.input : "";
  const onChange = (nextValue: string) =>
    fieldApi.onChange(nextValue === "" && fieldConfig.nullable ? emptyNullableValue(fieldConfig.nullable) : nextValue);
  const common = controlAttributes(props);
  const widget = fieldConfig.form ? fieldConfig.form.widget : undefined;

  if (widget === "textarea") {
    return (
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
    );
  }

  const isTime = widget === "time";
  return (
    <Input
      {...common}
      className="rounded-sm"
      type={isTime ? "time" : "text"}
      value={value}
      maxLength={isTime ? undefined : fieldConfig.maximumLength}
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
