import { type FormStore, getInput, useField } from "@formisch/react";
import type { ReactNode } from "react";
import { FieldGroup } from "@/modules/common/ui/field";
import { cn } from "@/modules/common/utils/cn";
import { BooleanField } from "./field-types/BooleanField";
import { DateTimeField } from "./field-types/DateTimeField";
import { NumberField } from "./field-types/NumberField";
import { RelationshipField } from "./field-types/RelationshipField";
import { RowField } from "./field-types/RowField";
import { SectionField } from "./field-types/SectionField";
import { SelectField } from "./field-types/SelectField";
import { TextField } from "./field-types/TextField";
import { UiField } from "./field-types/UiField";
import type {
  FieldLayout,
  FormFieldConfig,
  GroupCondition,
  NestedFieldsProps,
  RenderFieldsProps,
} from "./RenderFields.types";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
} from "./responsiveFieldLayout";

/** Renders `children` only while `condition` holds, re-evaluating as the form's values change. */
function ConditionalGroup<TDocument>({
  children,
  condition,
  form,
}: {
  children: ReactNode;
  condition: GroupCondition<TDocument>;
  form: FormStore;
}) {
  // ponytail: formisch 1.0.0-rc.0 only tracks reads inside a component that called one of its hooks,
  // and its types require a non-empty path. The empty path resolves to the root store, which is a
  // valid field store at runtime, so this subscribes to the whole form. Switch to a public
  // form-level subscription if formisch exports one.
  useField(form, { path: [] as unknown as [string] });
  return condition({ data: getInput(form) as Partial<TDocument> }) ? children : null;
}

// Section, row and ui carry no `name`, or share one with nothing, so they key by position.
function fieldKey<TDocument>(fieldConfig: FormFieldConfig<TDocument>, index: number) {
  if (fieldConfig.type === "section") return `${fieldConfig.labelKey}-${index}`;
  if (fieldConfig.type === "row") return `row-${index}`;
  return fieldConfig.name;
}

function RenderField<TDocument extends Record<string, unknown>>({
  fieldConfig,
  layout,
  ...nestedProps
}: NestedFieldsProps & { fieldConfig: FormFieldConfig<TDocument>; layout: FieldLayout }) {
  const { disabled, form, relationshipOptionAvailability, relationshipOptions } = nestedProps;
  const props = { disabled, form, layout, relationshipOptionAvailability, relationshipOptions };
  switch (fieldConfig.type) {
    case "section":
      return <SectionField {...nestedProps} fieldConfig={fieldConfig} layout={layout} />;
    case "row":
      return <RowField {...nestedProps} fieldConfig={fieldConfig} />;
    case "ui":
      return <UiField disabled={disabled} fieldConfig={fieldConfig} form={form} layout={layout} />;
    case "text":
      return <TextField {...props} fieldConfig={fieldConfig} />;
    case "number":
      return <NumberField {...props} fieldConfig={fieldConfig} />;
    case "boolean":
      return <BooleanField {...props} fieldConfig={fieldConfig} />;
    case "dateTime":
      return <DateTimeField {...props} fieldConfig={fieldConfig} />;
    case "select":
      return <SelectField {...props} fieldConfig={fieldConfig} />;
    case "relationship":
      return <RelationshipField {...props} fieldConfig={fieldConfig} />;
  }
  return null;
}

export function RenderFields<TDocument extends Record<string, unknown>>({
  fields,
  form,
  relationshipOptionAvailability = {},
  relationshipOptions = {},
  disabled = false,
  layout = "stacked",
  density = "comfortable",
  className,
}: RenderFieldsProps<TDocument>) {
  const nestedProps = { form, relationshipOptionAvailability, relationshipOptions, disabled, density };

  const renderedFields = fields
    .filter(
      (fieldConfig) =>
        fieldConfig.type === "section" ||
        fieldConfig.type === "row" ||
        fieldConfig.type === "ui" ||
        fieldConfig.form !== false,
    )
    .map((fieldConfig, index) => {
      const key = fieldKey(fieldConfig, index);
      const condition =
        fieldConfig.type === "section" || fieldConfig.type === "row" || fieldConfig.type === "ui"
          ? fieldConfig.condition
          : undefined;
      const field = <RenderField key={key} {...nestedProps} fieldConfig={fieldConfig} layout={layout} />;
      return condition ? (
        <ConditionalGroup key={key} condition={condition} form={form}>
          {field}
        </ConditionalGroup>
      ) : (
        field
      );
    });

  // Inline fields stack until this component's own container is wide enough
  // for the shared 12rem label column. Field details stay in the control column.
  return layout === "inline" ? (
    <div className={cn(RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME, className)}>
      <div className={cn(RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME, density === "compact" ? "gap-y-2" : "gap-y-4")}>
        {renderedFields}
      </div>
    </div>
  ) : (
    <FieldGroup density={density} className={cn(className)}>
      {renderedFields}
    </FieldGroup>
  );
}
