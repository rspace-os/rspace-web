import { Form, type FormStore, getInput, useField, useForm } from "@formisch/react";
import { ArchiveIcon, CircleCheckIcon, FileTextIcon, MapPinIcon, ScanSearchIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Switch } from "@/modules/common/ui/switch";
import { RenderFields } from "./RenderFields";
import type { FormFieldConfig, RelationshipOptions, ToOneRelationshipValue } from "./RenderFields.types";

export type ExampleDocument = {
  id: string;
  title: string;
  notes: string;
  score: number | null;
  enabled: boolean;
  modifiedAt: string | null;
  status: string | null;
  ownerId: ToOneRelationshipValue<{ id: string; name: string }> | null;
  collaboratorIds: string[];
};

function StatusOption({ detail, label }: { detail: string; label: string }) {
  return (
    <span className="flex flex-col">
      <span>{label}</span>
      <span className="text-xs text-muted-foreground" aria-hidden="true">
        {detail}
      </span>
    </span>
  );
}

export const exampleConfig = resolveCollectionConfig<ExampleDocument>({
  slug: "records",
  idField: "id",
  labels: {
    singularKey: "collectionForm.examples.record",
    pluralKey: "collectionForm.examples.records",
  },
  useAsTitle: "title",
  defaultColumns: ["title"],
  fields: [
    { name: "id", type: "text", labelKey: "collectionForm.examples.fields.id", form: false },
    {
      name: "title",
      type: "text",
      labelKey: "collectionForm.examples.fields.title",
      required: true,
      maximumLength: 80,
    },
    {
      name: "notes",
      type: "text",
      labelKey: "collectionForm.examples.fields.notes",
      form: {
        widget: "textarea",
        descriptionKey: "collectionForm.examples.fields.titleDescription",
        condition: ({ data, value }) => data.enabled === true && value !== "hide",
      },
    },
    {
      name: "score",
      type: "number",
      labelKey: "collectionForm.examples.fields.score",
      nullable: true,
      form: { width: "10rem" },
    },
    { name: "enabled", type: "boolean", labelKey: "collectionForm.examples.fields.enabled" },
    {
      name: "modifiedAt",
      type: "dateTime",
      labelKey: "collectionForm.examples.fields.modified",
      nullable: true,
    },
    {
      name: "status",
      type: "select",
      labelKey: "collectionForm.examples.fields.status",
      nullable: true,
      options: [
        "draft",
        { label: "In review", value: "review" },
        {
          content: <StatusOption detail="Ready to publish" label="Published" />,
          label: "Published",
          value: "published",
        },
      ],
    },
    {
      name: "ownerId",
      type: "relationship",
      labelKey: "collectionForm.examples.fields.owner",
      relationTo: "users",
      hasMany: false,
    },
    {
      name: "collaboratorIds",
      type: "relationship",
      labelKey: "collectionForm.examples.fields.collaborators",
      relationTo: "users",
      hasMany: true,
    },
  ],
});

const exampleFormFields: readonly FormFieldConfig<ExampleDocument>[] = [
  {
    type: "section",
    labelKey: "collectionForm.examples.recordDetails",
    fields: [
      {
        type: "row",
        fields: exampleConfig.fields.filter(
          (field) => field.name === "title" || field.name === "score" || field.name === "modifiedAt",
        ),
      },
      ...exampleConfig.fields.filter(
        (field) =>
          field.type !== "relationship" &&
          field.name !== "title" &&
          field.name !== "score" &&
          field.name !== "modifiedAt",
      ),
    ],
  },
  {
    type: "section",
    labelKey: "collectionForm.examples.relationships",
    fields: exampleConfig.fields.filter((field) => field.type === "relationship"),
  },
];

function exampleFields(...names: ReadonlyArray<keyof ExampleDocument>) {
  return exampleConfig.fields.filter((field) => names.includes(field.name));
}

const compactFormFields: readonly FormFieldConfig<ExampleDocument>[] = [
  { type: "row", fields: exampleFields("title", "score", "status") },
  { type: "row", fields: exampleFields("enabled", "modifiedAt", "ownerId") },
  ...exampleFields("notes", "collaboratorIds"),
];

const primaryFormFields = exampleFields("title", "notes", "enabled");
const metadataFormFields = exampleFields("score", "modifiedAt", "status");
const relationshipFormFields = exampleFields("ownerId", "collaboratorIds");
const presentationFormFields = exampleFields("title", "score", "status", "enabled", "ownerId", "collaboratorIds");
const itemDetailsFormFields = exampleFields(
  "title",
  "notes",
  "score",
  "status",
  "enabled",
  "ownerId",
  "collaboratorIds",
);

const presentationDescriptions: Record<keyof ExampleDocument, string> = {
  id: "The permanent identifier for this record.",
  title: "The name that identifies this record in lists and search results.",
  notes: "Background information that helps other people understand the record.",
  score: "A numeric quality score for the record.",
  enabled: "Controls whether this record is available to other people.",
  modifiedAt: "The date and time of the most recent change.",
  status: "The current stage of the review and publication process.",
  ownerId: "The person who is responsible for this record.",
  collaboratorIds: "The people who can contribute to this record.",
};

const controlOnlyClassName =
  "[&_[data-slot=field-content]]:sr-only [&_[data-slot=field-description]]:sr-only [&_[data-slot=field-label]]:sr-only";

function SettingsBooleanToggle({ disabled, form, label }: { disabled: boolean; form: FormStore; label: string }) {
  const fieldApi = useField(form, { path: ["enabled"] });
  return (
    <Switch
      aria-label={label}
      checked={fieldApi.input === true}
      disabled={disabled}
      inputRef={fieldApi.props.ref}
      name="enabled"
      onCheckedChange={(checked) => fieldApi.onChange(checked)}
    />
  );
}

function PersonOption({ detail, name }: { detail: string; name: string }) {
  return (
    <span className="flex flex-col">
      <span>{name}</span>
      <span className="text-xs text-muted-foreground" aria-hidden="true">
        {detail}
      </span>
    </span>
  );
}

const relationshipOptions: RelationshipOptions = {
  users: [
    { label: "Ada Lovelace", value: "user-1" },
    {
      content: <PersonOption detail="Rear admiral and computer scientist" name="Grace Hopper" />,
      label: "Grace Hopper",
      value: "user-2",
    },
    { label: "Katherine Johnson", value: "user-3" },
  ],
};

const defaultValues: ExampleDocument = {
  id: "RS-1042",
  title: "Organoid culture optimization",
  notes: "Initial notes",
  score: 92,
  enabled: true,
  modifiedAt: "2026-08-04T10:00:00Z",
  status: "draft",
  ownerId: {
    relationTo: "users",
    value: { id: "user-1", name: "Ada Lovelace" },
    globalId: "USER-1",
  },
  collaboratorIds: ["user-2"],
};

export function RenderFieldsStory({
  disabled = false,
  presentation = "sectioned",
  sectionVariant,
}: {
  disabled?: boolean;
  presentation?:
    | "sectioned"
    | "compact"
    | "split"
    | "progressive"
    | "settings"
    | "aligned"
    | "prompt-cards"
    | "item-details";
  sectionVariant?: "card" | "transparent";
}) {
  const { t } = useTranslation("common");
  const schema = v.object({
    id: v.string(),
    title: v.pipe(v.string(), v.nonEmpty("Title is required.")),
    notes: v.string(),
    score: v.nullable(v.number()),
    enabled: v.boolean(),
    modifiedAt: v.nullable(v.string()),
    status: v.nullable(v.string()),
    ownerId: v.nullable(
      v.object({
        relationTo: v.string(),
        value: v.unknown(),
        globalId: v.optional(v.string()),
      }),
    ),
    collaboratorIds: v.array(v.string()),
  });
  const form = useForm({
    schema,
    initialInput: defaultValues,
    validate: "input",
  });

  const fieldProps = {
    disabled,
    form,
    relationshipOptions,
  };

  let fields: ReactNode;
  switch (presentation) {
    case "compact":
      fields = (
        <RenderFields {...fieldProps} className="rounded-sm border bg-card p-6 shadow-sm" fields={compactFormFields} />
      );
      break;
    case "split":
      fields = (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
          <RenderFields {...fieldProps} fields={primaryFormFields} />
          <aside className="space-y-6">
            <RenderFields
              {...fieldProps}
              fields={[
                {
                  type: "section",
                  labelKey: "collectionForm.examples.recordDetails",
                  fields: metadataFormFields,
                },
                {
                  type: "section",
                  labelKey: "collectionForm.examples.relationships",
                  fields: relationshipFormFields,
                },
              ]}
            />
          </aside>
        </div>
      );
      break;
    case "progressive":
      fields = (
        <div className="space-y-6">
          <RenderFields {...fieldProps} fields={primaryFormFields} />
          <details className="rounded-sm border bg-card p-4">
            <summary className="cursor-pointer font-medium">{t("collectionForm.examples.recordDetails")}</summary>
            <RenderFields {...fieldProps} className="pt-5" fields={metadataFormFields} />
          </details>
          <details className="rounded-sm border bg-card p-4">
            <summary className="cursor-pointer font-medium">{t("collectionForm.examples.relationships")}</summary>
            <RenderFields {...fieldProps} className="pt-5" fields={relationshipFormFields} />
          </details>
        </div>
      );
      break;
    case "settings":
      fields = (
        <div className="divide-y rounded-sm border bg-card shadow-sm">
          {presentationFormFields.map((field) => (
            <div
              key={field.name}
              className="grid gap-5 px-6 py-5 md:grid-cols-[minmax(0,1fr)_minmax(16rem,0.8fr)] md:items-center"
            >
              <div className="space-y-1">
                <h2 className="text-sm font-medium">{t(field.labelKey as never)}</h2>
                <p className="max-w-xl text-sm text-muted-foreground">{presentationDescriptions[field.name]}</p>
              </div>
              <div className="flex w-full justify-start md:justify-end">
                {field.name === "enabled" ? (
                  <SettingsBooleanToggle disabled={disabled} form={form} label={t(field.labelKey as never)} />
                ) : (
                  <RenderFields
                    {...fieldProps}
                    className={`${controlOnlyClassName} w-full md:max-w-sm`}
                    fields={[field]}
                  />
                )}
              </div>
            </div>
          ))}
        </div>
      );
      break;
    case "aligned":
      fields = (
        <div className="rounded-sm border bg-card px-6 shadow-sm">
          {presentationFormFields.map((field) => (
            <div
              key={field.name}
              className="grid gap-2 border-b py-4 last:border-b-0 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-6"
            >
              <h2 className="pt-2 text-sm font-medium">{t(field.labelKey as never)}</h2>
              <div className="space-y-2">
                <RenderFields {...fieldProps} className={controlOnlyClassName} fields={[field]} />
                <p className="text-xs text-muted-foreground">{presentationDescriptions[field.name]}</p>
              </div>
            </div>
          ))}
        </div>
      );
      break;
    // Mirrors the "Booking rules" card on the View Bookable Item page
    // (BookableItemPage.tsx), including its responsive inline form layout.
    case "item-details":
      fields = (
        <Card>
          <CardHeader>
            <CardTitle>{t("collectionForm.examples.recordDetails")}</CardTitle>
          </CardHeader>
          <CardContent>
            <RenderFields {...fieldProps} fields={itemDetailsFormFields} layout="inline" />
          </CardContent>
        </Card>
      );
      break;
    case "prompt-cards":
      fields = (
        <div className="grid gap-4 md:grid-cols-2">
          {presentationFormFields.map((field) => (
            <section key={field.name} className="flex flex-col gap-5 rounded-sm border bg-card p-5 shadow-sm">
              <div className="space-y-1">
                <h2 className="font-medium">{t(field.labelKey as never)}</h2>
                <p className="text-sm text-muted-foreground">{presentationDescriptions[field.name]}</p>
              </div>
              <RenderFields {...fieldProps} className={`${controlOnlyClassName} mt-auto`} fields={[field]} />
            </section>
          ))}
        </div>
      );
      break;
    default:
      fields = (
        <RenderFields
          {...fieldProps}
          fields={exampleFormFields.map((field) =>
            field.type === "section" ? { ...field, variant: sectionVariant } : field,
          )}
        />
      );
  }

  return (
    <Form of={form} className="mx-auto max-w-5xl space-y-8 p-8" onSubmit={() => undefined}>
      {fields}
      <output aria-label="Form values">{JSON.stringify(getInput(form))}</output>
    </Form>
  );
}

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
    <Form of={form} className="mx-auto max-w-5xl space-y-8 p-8" onSubmit={() => undefined}>
      <RenderFields fields={cardSelectConfig.fields} form={form} />
      <output aria-label="Form values">{JSON.stringify(getInput(form))}</output>
    </Form>
  );
}

type InventoryRelationshipDocument = {
  inventoryItemId: ToOneRelationshipValue<string> | null;
};

const inventoryRelationshipConfig = resolveCollectionConfig<InventoryRelationshipDocument>({
  slug: "inventory-links",
  idField: "inventoryItemId",
  labels: {
    singularKey: "collectionForm.examples.inventoryItem",
    pluralKey: "collectionForm.examples.inventoryItem",
  },
  useAsTitle: "inventoryItemId",
  defaultColumns: ["inventoryItemId"],
  fields: [
    {
      name: "inventoryItemId",
      type: "relationship",
      labelKey: "collectionForm.examples.inventoryItem",
      relationTo: "inventoryItems",
      hasMany: false,
      nullable: true,
    },
  ],
});

function InventoryRelationshipOption({
  globalId,
  location,
  name,
}: {
  globalId: string;
  location: string;
  name: string;
}) {
  return (
    <>
      <span className="sr-only">{name}</span>
      <div className="pointer-events-none w-full" aria-hidden="true" inert>
        <InventoryItem
          name={name}
          globalId={globalId}
          href={`/inventory/${globalId}`}
          idLinkLabel={`Open inventory record ${globalId}`}
          size="xs"
        >
          <MapPinIcon aria-hidden="true" className="size-3.5 shrink-0" />
          {location}
        </InventoryItem>
      </div>
    </>
  );
}

const inventoryRelationshipOptions: RelationshipOptions = {
  inventoryItems: [
    {
      content: (
        <InventoryRelationshipOption globalId="IC-LSM900" location="Imaging suite 1.02" name="Zeiss LSM 900 confocal" />
      ),
      label: "Zeiss LSM 900 confocal",
      value: "IC-LSM900",
    },
    {
      content: (
        <InventoryRelationshipOption
          globalId="IC-COLD4"
          location="Biobank freezer F-2"
          name="Biobank ultra-low freezer"
        />
      ),
      label: "Biobank ultra-low freezer",
      value: "IC-COLD4",
    },
  ],
};

export function InventoryRelationshipStory() {
  const form = useForm({
    schema: v.object({
      inventoryItemId: v.nullable(
        v.object({
          relationTo: v.string(),
          value: v.string(),
        }),
      ),
    }),
    initialInput: { inventoryItemId: null } satisfies InventoryRelationshipDocument,
  });

  return (
    <Form of={form} className="mx-auto max-w-2xl p-8" onSubmit={() => undefined}>
      <RenderFields
        fields={inventoryRelationshipConfig.fields}
        form={form}
        relationshipOptions={inventoryRelationshipOptions}
      />
    </Form>
  );
}
