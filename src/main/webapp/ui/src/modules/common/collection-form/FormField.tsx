import { type FormStore, getInput, useField } from "@formisch/react";
import { type ReactNode, useId } from "react";
import { useTranslation } from "react-i18next";
import type { FieldConditionContext, ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Field, FieldContent, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import type { ControlProps, FieldLayout, RelationshipOptions } from "./RenderFields.types";
import { RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME } from "./responsiveFieldLayout";

export function FormField<
  TDocument extends Record<string, unknown>,
  TType extends ResolvedFieldConfig<TDocument>["type"] = ResolvedFieldConfig<TDocument>["type"],
>({
  children,
  disabled,
  fieldConfig,
  form,
  layout = "stacked",
  orientation = "vertical",
  relationshipOptionAvailability,
  relationshipOptions,
}: {
  children: (props: ControlProps<TDocument, TType>) => ReactNode;
  disabled: boolean;
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: TType }>;
  form: FormStore;
  layout?: FieldLayout;
  orientation?: "horizontal" | "vertical";
  relationshipOptionAvailability: ControlProps<TDocument, TType>["relationshipOptionAvailability"];
  relationshipOptions: RelationshipOptions;
}) {
  const { t } = useTranslation("common");
  const id = `${useId()}-${fieldConfig.name}`;
  const path: [string] = [fieldConfig.name];
  const fieldApi = useField(form, { path });
  const formConfig = fieldConfig.form || undefined;

  if (
    formConfig?.condition &&
    !formConfig.condition({
      data: getInput(form) as Partial<TDocument>,
      value: fieldApi.input as FieldConditionContext<TDocument>["value"],
    })
  ) {
    return null;
  }

  const descriptionId = formConfig?.descriptionKey ? `${id}-description` : undefined;
  const errors = fieldApi.errors?.map((message) => ({ message })) ?? [];
  const invalid = errors.length > 0;
  const errorId = invalid ? `${id}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(" ") || undefined;
  const label = t(fieldConfig.labelKey as never);
  const labelBody = (
    <>
      {label}
      {fieldConfig.required ? <span aria-hidden="true">{"*"}</span> : null}
    </>
  );
  const control = children({
    describedBy,
    disabled,
    fieldApi,
    fieldConfig,
    id,
    invalid,
    label,
    relationshipOptionAvailability,
    relationshipOptions,
  });
  const details = (
    <>
      {formConfig?.descriptionKey ? (
        <FieldDescription id={descriptionId}>{t(formConfig.descriptionKey as never)}</FieldDescription>
      ) : null}
      <FieldError id={errorId} errors={errors} />
    </>
  );

  // Inline wins over `orientation`: a two-column grid has one place for the
  // label and one for the control, whichever order the field type prefers.
  if (layout === "inline") {
    return (
      <Field
        className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}
        data-disabled={disabled || fieldConfig.readOnly}
        data-invalid={invalid}
      >
        {/* Only once the row is two columns: the label cell would otherwise stretch to the row and
            drop its text to the top, above a control centred in its own 36 px line. self-start keeps
            the label box to that same 36 px at the top of the row, so a tall control (a textarea)
            aligns the label with its first line rather than its middle. Stacked, the label sits
            directly above its control and needs no line box of its own. */}
        <FieldLabel htmlFor={id} className="@md:min-h-9 @md:items-center @md:self-start">
          {labelBody}
        </FieldLabel>
        {/* min-h-9 matches an input's height, so a short control (a switch) keeps
            the row the same height as the read-out row it replaces. */}
        <FieldContent className="min-h-9 justify-center">
          {control}
          {details}
        </FieldContent>
      </Field>
    );
  }

  return orientation === "horizontal" ? (
    <Field orientation="horizontal" data-disabled={disabled || fieldConfig.readOnly} data-invalid={invalid}>
      {control}
      <FieldContent>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {details}
      </FieldContent>
    </Field>
  ) : (
    <Field data-disabled={disabled || fieldConfig.readOnly} data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{labelBody}</FieldLabel>
      {control}
      {details}
    </Field>
  );
}
