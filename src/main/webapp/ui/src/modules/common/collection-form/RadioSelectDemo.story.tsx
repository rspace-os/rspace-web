import { useForm } from "@formisch/react";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFields } from "./RenderFields";
import { FormWithValues } from "./renderFieldsStorySupport.story";
import "./__tests__/i18n/collectionFormExamples";

type RadioSelectDocument = {
  allowOff: boolean;
  subscription: string;
};

const radioSelectOptions = [
  { label: "On", value: "on" },
  { label: "Off", value: "off" },
] as const;

const radioSelectConfig = resolveCollectionConfig<RadioSelectDocument>({
  slug: "radio-select-example",
  idField: "subscription",
  labels: {
    singularKey: "collectionFormExamples:record",
    pluralKey: "collectionFormExamples:records",
  },
  useAsTitle: "subscription",
  defaultColumns: ["subscription"],
  fields: [
    { name: "allowOff", type: "boolean", labelKey: "collectionFormExamples:fields.enabled" },
    {
      name: "subscription",
      type: "select",
      labelKey: "collectionFormExamples:fields.status",
      required: true,
      form: {
        widget: "radio",
        descriptionKey: "collectionFormExamples:fields.titleDescription",
        isOptionDisabled: (option, data) => {
          const value = typeof option === "string" ? option : option.value;
          return value === "off" && data.allowOff !== true;
        },
      },
      options: radioSelectOptions,
    },
  ],
});

export function RadioSelectStory({ disabled = false, readOnly = false }: { disabled?: boolean; readOnly?: boolean }) {
  const form = useForm({
    schema: v.object({
      allowOff: v.boolean(),
      subscription: v.pipe(v.string(), v.nonEmpty("Choose a subscription.")),
    }),
    initialInput: { allowOff: false, subscription: "on" },
    validate: "input",
  });
  const fields = radioSelectConfig.fields.map((field) =>
    field.name === "subscription" ? { ...field, readOnly } : field,
  );

  return (
    <FormWithValues form={form}>
      <RenderFields fields={fields} form={form} disabled={disabled} />
    </FormWithValues>
  );
}
