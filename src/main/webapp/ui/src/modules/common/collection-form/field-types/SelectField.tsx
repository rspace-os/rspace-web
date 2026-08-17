import { useTranslation } from "react-i18next";
import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Combobox, ComboboxContent, ComboboxInput, ComboboxItem, ComboboxList } from "@/modules/common/ui/combobox";
import { controlAttributes } from "../controlAttributes";
import { FormField } from "../FormField";
import type { ControlProps, FieldRendererProps } from "../RenderFields.types";

type SelectFieldControlProps<TDocument> = Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "select" }>;
};

function SelectFieldControl<TDocument>(props: SelectFieldControlProps<TDocument>) {
  const { disabled, fieldApi, fieldConfig, label } = props;
  const { t } = useTranslation("common");
  const options = fieldConfig.options.map((option) =>
    typeof option === "string" ? { label: option, value: option } : option,
  );
  const selected = options.find((option) => option.value === fieldApi.input) ?? null;

  return (
    <Combobox
      autoComplete="both"
      autoHighlight
      items={options}
      value={selected}
      disabled={disabled || fieldConfig.readOnly}
      isItemEqualToValue={(option, value) => option.value === value.value}
      itemToStringLabel={(option) => option.label}
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
            <ComboboxItem key={option.value} value={option} aria-label={option.label}>
              {"content" in option ? (option.content ?? option.label) : option.label}
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
      {(controlProps) => <SelectFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
