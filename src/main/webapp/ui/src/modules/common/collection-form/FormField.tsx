import { type FormStore, getInput, useField } from "@formisch/react";
import { type ReactNode, useId } from "react";
import { useTranslation } from "react-i18next";
import type { FieldConditionContext, ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { Field, FieldContent, FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import type { ControlProps, FieldLayout, RelationshipOptions } from "./RenderFields.types";
import { RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME } from "./responsiveFieldLayout";

function messageOf(error: unknown): string {
  if (typeof error === "string") return error;
  if (error instanceof Error) return error.message;
  if (typeof error === "object" && error !== null && "message" in error) return String(error.message);
  return String(error);
}

export function FormField<TDocument extends Record<string, unknown>>({
  children,
  disabled,
  fieldConfig,
  form,
  layout = "stacked",
  orientation = "vertical",
  relationshipOptionAvailability,
  relationshipOptions,
}: {
  children: (props: ControlProps<TDocument>) => ReactNode;
  disabled: boolean;
  fieldConfig: ResolvedFieldConfig<TDocument>;
  form: FormStore;
  layout?: FieldLayout;
  orientation?: "horizontal" | "vertical";
  relationshipOptionAvailability: ControlProps<TDocument>["relationshipOptionAvailability"];
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
      field: {
        errors: fieldApi.errors,
        isDirty: fieldApi.isDirty,
        isEdited: fieldApi.isEdited,
        isTouched: fieldApi.isTouched,
        isValid: fieldApi.isValid,
      },
      form: {
        errors: form.errors,
        isDirty: form.isDirty,
        isEdited: form.isEdited,
        isSubmitting: form.isSubmitting,
        isSubmitted: form.isSubmitted,
        isTouched: form.isTouched,
        isValid: form.isValid,
        isValidating: form.isValidating,
      },
    })
  ) {
    return null;
  }

  const descriptionId = formConfig?.descriptionKey ? `${id}-description` : undefined;
  const errors = fieldApi.errors?.map((error) => ({ message: messageOf(error) })) ?? [];
  const invalid = errors.length > 0;
  const errorId = invalid ? `${id}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(" ") || undefined;
  const label = t(fieldConfig.labelKey as never);
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
        <FieldLabel htmlFor={id}>
          {label}
          {fieldConfig.required ? <span aria-hidden="true">{"*"}</span> : null}
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
      <FieldLabel htmlFor={id}>
        {label}
        {fieldConfig.required ? <span aria-hidden="true">{"*"}</span> : null}
      </FieldLabel>
      {control}
      {details}
    </Field>
  );
}
