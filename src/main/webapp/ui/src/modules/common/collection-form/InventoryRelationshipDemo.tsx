import { Form, useForm } from "@formisch/react";
import { MapPinIcon } from "lucide-react";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { RenderFields } from "./RenderFields";
import type { RelationshipOptions, ToOneRelationshipValue } from "./RenderFields.types";

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
