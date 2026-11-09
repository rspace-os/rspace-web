import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { SelectOption } from "@/modules/common/collection/config";
import { selectOptionText } from "@/modules/common/collection/utils";
import { Combobox, ComboboxContent, ComboboxInput, ComboboxItem, ComboboxList } from "@/modules/common/ui/combobox";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import { emptyNullableValue } from "../fieldValues";
import { formData } from "../formData";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type SelectFieldControlProps<TDocument> = ControlProps<TDocument, "select"> & {
  options: readonly NormalizedSelectOption[];
};

type NormalizedSelectOption = {
  content: ReactNode;
  disabled: boolean;
  textValue: string;
  value: string;
};

function normalizeOption(option: SelectOption, disabled: boolean): NormalizedSelectOption {
  if (typeof option === "string") return { content: option, disabled, textValue: option, value: option };
  return {
    content: "content" in option ? (option.content ?? option.label) : option.label,
    disabled,
    textValue: selectOptionText(option),
    value: option.value,
  };
}

function CardSelectFieldControl<TDocument extends Record<string, unknown>>(props: SelectFieldControlProps<TDocument>) {
  const { disabled, fieldApi, fieldConfig, label, options } = props;

  return (
    <RadioGroup
      {...controlAttributes(props)}
      aria-label={label}
      className="grid-cols-1 sm:grid-cols-2 lg:grid-cols-3"
      disabled={disabled}
      inputRef={fieldApi.props.ref}
      onValueChange={(value) => fieldApi.onChange(value)}
      readOnly={fieldConfig.readOnly}
      value={typeof fieldApi.input === "string" ? fieldApi.input : undefined}
    >
      {options.map((option) => (
        <RadioGroupItem
          aria-label={option.textValue}
          className="aspect-auto size-auto min-h-20 w-full cursor-pointer items-start justify-start gap-3 rounded-sm border bg-card p-4 text-left shadow-sm transition-colors after:hidden focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50 data-checked:border-primary/40 data-checked:bg-primary/5"
          disabled={option.disabled}
          key={option.value}
          value={option.value}
        >
          <span className="min-w-0">{option.content}</span>
        </RadioGroupItem>
      ))}
    </RadioGroup>
  );
}

function RadioSelectFieldControl<TDocument extends Record<string, unknown>>(props: SelectFieldControlProps<TDocument>) {
  const { disabled, fieldApi, fieldConfig, id, label, options } = props;

  return (
    <RadioGroup
      {...controlAttributes(props)}
      aria-label={label}
      className="grid-cols-1 gap-x-5 gap-y-1 sm:flex sm:flex-wrap sm:items-center"
      disabled={disabled}
      inputRef={fieldApi.props.ref}
      onValueChange={(value) => fieldApi.onChange(value)}
      readOnly={fieldConfig.readOnly}
      value={typeof fieldApi.input === "string" ? fieldApi.input : undefined}
    >
      {options.map((option, index) => {
        const optionId = `${id}-option-${index}`;
        const optionLabelId = `${optionId}-label`;

        return (
          <div className="flex items-center gap-2" key={option.value}>
            <RadioGroupItem
              aria-labelledby={optionLabelId}
              disabled={option.disabled}
              id={optionId}
              value={option.value}
            />
            <label
              className={`select-none ${disabled || fieldConfig.readOnly || option.disabled ? "cursor-default" : "cursor-pointer"}`}
              htmlFor={optionId}
              id={optionLabelId}
            >
              {option.content}
            </label>
          </div>
        );
      })}
    </RadioGroup>
  );
}

function ComboboxSelectFieldControl<TDocument extends Record<string, unknown>>(
  props: SelectFieldControlProps<TDocument>,
) {
  const { disabled, fieldApi, fieldConfig, label, options } = props;
  const { t } = useTranslation("common");
  const selected = options.find((option) => option.value === fieldApi.input) ?? null;

  return (
    <Combobox
      autoComplete="both"
      autoHighlight
      items={options}
      value={selected}
      disabled={disabled || fieldConfig.readOnly}
      isItemEqualToValue={(option, value) => option.value === value.value}
      itemToStringLabel={(option) => option.textValue}
      onValueChange={(option) => fieldApi.onChange(option?.value ?? emptyNullableValue(fieldConfig.nullable))}
    >
      <ComboboxInput
        {...controlAttributes(props)}
        className="rounded-sm"
        clearLabel={t("collectionForm.actions.clear", { field: label })}
        disabled={disabled || fieldConfig.readOnly}
        showClear={!fieldConfig.required}
        triggerLabel={t("collectionForm.actions.openOptions", { field: label })}
        ref={fieldApi.props.ref}
        onFocus={fieldApi.props.onFocus}
        onBlur={fieldApi.props.onBlur}
      />
      <ComboboxContent>
        <ComboboxList>
          {(option: (typeof options)[number]) => (
            <ComboboxItem key={option.value} value={option} aria-label={option.textValue} disabled={option.disabled}>
              {option.content}
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  );
}

export function SelectField<TDocument extends Record<string, unknown>>(props: FieldRendererProps<TDocument, "select">) {
  return (
    <FormField {...props}>
      {(controlProps) => {
        const { fieldApi } = controlProps;
        const { fieldConfig } = props;
        const formConfig = fieldConfig.form || undefined;
        const widget = formConfig?.widget ?? "select";

        if (Array.isArray(fieldApi.input)) {
          if (widget === "card") throw new Error("Card select does not support multiple values");
          if (widget === "radio") throw new Error("Radio select does not support multiple values");
        }

        const isOptionDisabled = formConfig?.isOptionDisabled;
        const data = isOptionDisabled ? formData<TDocument>(props.form) : undefined;
        const options = fieldConfig.options.map((option) =>
          normalizeOption(option, isOptionDisabled && data ? isOptionDisabled(option, data) : false),
        );
        const controlPropsWithOptions = {
          ...controlProps,
          fieldConfig,
          options,
        };
        const controls: Record<typeof widget, (props: SelectFieldControlProps<TDocument>) => ReactNode> = {
          card: (controlProps) => <CardSelectFieldControl {...controlProps} />,
          radio: (controlProps) => <RadioSelectFieldControl {...controlProps} />,
          select: (controlProps) => <ComboboxSelectFieldControl {...controlProps} />,
        };

        return controls[widget](controlPropsWithOptions);
      }}
    </FormField>
  );
}
