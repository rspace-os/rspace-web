import { FieldGroup } from "@/modules/common/ui/field";
import { cn } from "@/modules/common/utils/cn";
import { BooleanField } from "./field-types/BooleanField";
import { DateTimeField } from "./field-types/DateTimeField";
import { NumberField } from "./field-types/NumberField";
import { RelationshipField } from "./field-types/RelationshipField";
import { RowField, RowFieldItem } from "./field-types/RowField";
import { SectionField } from "./field-types/SectionField";
import { SelectField } from "./field-types/SelectField";
import { TextField } from "./field-types/TextField";
import type { RenderFieldsProps } from "./RenderFields.types";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
} from "./responsiveFieldLayout";

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
  const renderedFields = fields
    .filter(
      (fieldConfig) =>
        fieldConfig.type === "section" ||
        fieldConfig.type === "row" ||
        fieldConfig.type === "ui" ||
        fieldConfig.form !== false,
    )
    .map((fieldConfig, index) => {
      if (fieldConfig.type === "section") {
        const key = `${fieldConfig.labelKey}-${index}`;
        const section = (
          <SectionField key={key} labelKey={fieldConfig.labelKey} variant={fieldConfig.variant}>
            <RenderFields
              fields={fieldConfig.fields}
              form={form}
              relationshipOptionAvailability={relationshipOptionAvailability}
              relationshipOptions={relationshipOptions}
              disabled={disabled}
              layout={layout}
              density={density}
            />
          </SectionField>
        );

        // A section is a block, not a label/control pair, so inside an inline
        // list it spans both columns instead of sitting in the label one.
        return layout === "inline" ? (
          <div key={key} className="@md:col-span-2">
            {section}
          </div>
        ) : (
          section
        );
      }

      if (fieldConfig.type === "row") {
        return (
          // A row is its own horizontal grouping, so its fields stay stacked
          // even inside an inline list.
          <RowField key={`row-${index}`}>
            {fieldConfig.fields
              .filter((field) => field.form !== false)
              .map((field) => (
                <RowFieldItem key={field.name} width={field.form ? field.form.width : undefined}>
                  <RenderFields
                    fields={[field]}
                    form={form}
                    relationshipOptionAvailability={relationshipOptionAvailability}
                    relationshipOptions={relationshipOptions}
                    disabled={disabled}
                    density={density}
                  />
                </RowFieldItem>
              ))}
          </RowField>
        );
      }

      if (fieldConfig.type === "ui") {
        // Rendered as a component, not called as a function, so its own hooks are its own.
        const Ui = fieldConfig.component;
        const ui = <Ui key={fieldConfig.name} form={form} disabled={disabled} />;
        // Like a section, a ui block is not a label/control pair, so it spans both columns.
        return layout === "inline" ? (
          <div key={fieldConfig.name} className="@md:col-span-2">
            {ui}
          </div>
        ) : (
          ui
        );
      }

      const props = { disabled, form, layout, relationshipOptionAvailability, relationshipOptions };
      switch (fieldConfig.type) {
        case "text":
          return <TextField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
        case "number":
          return <NumberField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
        case "boolean":
          return <BooleanField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
        case "dateTime":
          return <DateTimeField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
        case "select":
          return <SelectField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
        case "relationship":
          return <RelationshipField key={fieldConfig.name} {...props} fieldConfig={fieldConfig} />;
      }

      return null;
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
