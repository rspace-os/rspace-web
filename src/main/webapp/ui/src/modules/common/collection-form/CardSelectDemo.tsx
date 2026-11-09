import { useForm } from "@formisch/react";
import { ArchiveIcon, CircleCheckIcon, FileTextIcon, ScanSearchIcon } from "lucide-react";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFields } from "./RenderFields";
import { FormWithValues } from "./renderFieldsStorySupport.story";

type CardSelectDocument = {
  enabled: boolean;
  status: string;
};

function CardOptionLabel({ icon: Icon, label }: { icon: typeof FileTextIcon; label: string }) {
  return (
    <span className="flex items-center gap-3">
      <Icon aria-hidden="true" className="size-5 shrink-0" />
      <span>{label}</span>
    </span>
  );
}

const cardSelectConfig = resolveCollectionConfig<CardSelectDocument>({
  slug: "card-select-example",
  idField: "status",
  labels: {
    singularKey: "collectionForm.examples.record",
    pluralKey: "collectionForm.examples.records",
  },
  useAsTitle: "status",
  defaultColumns: ["status"],
  fields: [
    { name: "enabled", type: "boolean", labelKey: "collectionForm.examples.fields.enabled" },
    {
      name: "status",
      type: "select",
      labelKey: "collectionForm.examples.fields.status",
      form: {
        widget: "card",
        isOptionDisabled: (option, data) => {
          const value = typeof option === "string" ? option : option.value;
          return value === "archived" || (value === "published" && data.enabled !== true);
        },
      },
      options: [
        {
          label: <CardOptionLabel icon={FileTextIcon} label="Draft" />,
          textValue: "Draft",
          value: "draft",
        },
        {
          label: <CardOptionLabel icon={ScanSearchIcon} label="In review" />,
          textValue: "In review",
          value: "review",
        },
        {
          label: <CardOptionLabel icon={CircleCheckIcon} label="Published" />,
          textValue: "Published",
          value: "published",
        },
        {
          label: <CardOptionLabel icon={ArchiveIcon} label="Archived" />,
          textValue: "Archived",
          value: "archived",
        },
      ],
    },
  ],
});

export function CardSelectStory() {
  const form = useForm({
    schema: v.object({ enabled: v.boolean(), status: v.string() }),
    initialInput: { enabled: false, status: "draft" },
  });

  return (
    <FormWithValues form={form}>
      <RenderFields fields={cardSelectConfig.fields} form={form} />
    </FormWithValues>
  );
}
