import { type FormStore, getInput } from "@formisch/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  type ResolvedFieldConfig,
  type SelectOption,
  selectOptionText,
} from "@/modules/common/collection/collectionConfig";
import { Combobox, ComboboxContent, ComboboxInput, ComboboxItem, ComboboxList } from "@/modules/common/ui/combobox";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type SelectFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "select" }>;
  form: FormStore;
};

type NormalizedSelectOption = {
  content: ReactNode;
  source: SelectOption;
  textValue: string;
  value: string;
};

function normalizeOption(option: SelectOption): NormalizedSelectOption {
  if (typeof option === "string") return { content: option, source: option, textValue: option, value: option };
  return {
    content: "content" in option ? (option.content ?? option.label) : option.label,
    source: option,
    textValue: selectOptionText(option),
    value: option.value,
  };
}

function CardSelectFieldControl<TDocument extends Record<string, unknown>>(props: SelectFieldControlProps<TDocument>) {
  const { disabled, fieldApi, fieldConfig, form, label } = props;
  if (Array.isArray(fieldApi.input)) throw new Error("Card select does not support multiple values");
  const options = fieldConfig.options.map(normalizeOption);
  const data = getInput(form) as Partial<TDocument>;
  const formConfig = fieldConfig.form || undefined;
  const isOptionDisabled = formConfig?.isOptionDisabled;

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
          disabled={isOptionDisabled ? isOptionDisabled(option.source, data) : false}
          key={option.value}
          value={option.value}
        >
          <span className="min-w-0">{option.content}</span>
        </RadioGroupItem>
      ))}
    </RadioGroup>
  );
}

function ComboboxSelectFieldControl<TDocument extends Record<string, unknown>>(
  props: SelectFieldControlProps<TDocument>,
) {
  const { disabled, fieldApi, fieldConfig, form, label } = props;
  const { t } = useTranslation("common");
  const options = fieldConfig.options.map(normalizeOption);
  const data = getInput(form) as Partial<TDocument>;
  const formConfig = fieldConfig.form || undefined;
  const isOptionDisabled = formConfig?.isOptionDisabled;
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
      onValueChange={(option) => fieldApi.onChange(option?.value ?? (fieldConfig.nullable ? null : undefined))}
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
            <ComboboxItem
              key={option.value}
              value={option}
              aria-label={option.textValue}
              disabled={isOptionDisabled ? isOptionDisabled(option.source, data) : false}
            >
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
      {(controlProps) =>
        props.fieldConfig.form && props.fieldConfig.form.widget === "card" ? (
          <CardSelectFieldControl {...controlProps} fieldConfig={props.fieldConfig} form={props.form} />
        ) : (
          <ComboboxSelectFieldControl {...controlProps} fieldConfig={props.fieldConfig} form={props.form} />
        )
      }
    </FormField>
  );
}
