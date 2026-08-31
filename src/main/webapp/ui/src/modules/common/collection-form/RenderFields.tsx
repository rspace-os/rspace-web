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

export function RenderFields<TDocument extends Record<string, unknown>>({
  fields,
  form,
  relationshipOptionAvailability = {},
  relationshipOptions = {},
  disabled = false,
  className,
}: RenderFieldsProps<TDocument>) {
  return (
    <FieldGroup className={cn(className)}>
      {fields
        .filter(
          (fieldConfig) => fieldConfig.type === "section" || fieldConfig.type === "row" || fieldConfig.form !== false,
        )
        .map((fieldConfig, index) => {
          if (fieldConfig.type === "section") {
            return (
              <SectionField
                key={`${fieldConfig.labelKey}-${index}`}
                labelKey={fieldConfig.labelKey}
                variant={fieldConfig.variant}
              >
                <RenderFields
                  fields={fieldConfig.fields}
                  form={form}
                  relationshipOptionAvailability={relationshipOptionAvailability}
                  relationshipOptions={relationshipOptions}
                  disabled={disabled}
                />
              </SectionField>
            );
          }

          if (fieldConfig.type === "row") {
            return (
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
                      />
                    </RowFieldItem>
                  ))}
              </RowField>
            );
          }

          const props = { disabled, form, relationshipOptionAvailability, relationshipOptions };
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
        })}
    </FieldGroup>
  );
}
