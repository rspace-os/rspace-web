import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { NuqsAdapter } from "nuqs/adapters/react";
import * as v from "valibot";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import type { ApiV2CollectionMetadata } from "./adapters/apiV2/apiV2CollectionMetadata";
import { useApiV2TableList } from "./adapters/apiV2/useApiV2TableList";
import { TableList } from "./TableList";
import { type ResearchRecord, storyConfig, storyRecords } from "./TableList.stories";

const documentSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.string(),
  score: v.number(),
  enabled: v.boolean(),
  modifiedAt: v.string(),
});

// Production modules read this object from the generated API V2 OpenAPI metadata.
const metadata: ApiV2CollectionMetadata<ResearchRecord> = {
  resourceName: "records",
  fields: ["id", "title", "owner", "score", "enabled", "modifiedAt"],
  sorting: {
    fields: ["title", "owner", "score", "enabled", "modifiedAt"],
    default: [{ field: "modifiedAt", direction: "desc" }],
    maximumFields: 5,
  },
  filtering: {
    selectors: {
      title: { operators: ["==", "!=", "=contains=", "=like="], wildcards: true },
      owner: { operators: ["==", "!=", "=contains="], wildcards: true },
      score: { operators: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le="], wildcards: false },
      enabled: { operators: ["==", "!=", "=exists="], wildcards: false },
      modifiedAt: { operators: ["==", "=gt=", "=ge=", "=lt=", "=le="], wildcards: false },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 5, maximumLimit: 100 },
};

const storyFetch: typeof globalThis.fetch = async () =>
  new Response(
    JSON.stringify({
      docs: storyRecords.slice(0, 5),
      totalDocs: 37,
      limit: 5,
      page: 1,
      pagingCounter: 1,
      totalPages: 8,
      hasPrevPage: false,
      hasNextPage: true,
      prevPage: null,
      nextPage: 2,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );

function ApiV2Demo() {
  const table = useApiV2TableList({
    resourceName: "records",
    config: storyConfig,
    documentSchema,
    metadata,
    request: { fetch: storyFetch },
    query: { keepPreviousData: true },
  });
  const request = `/api/v2/records?${table.adapter.toSearchParams(table.state)}`;

  return (
    <div className="mx-auto max-w-7xl p-8">
      <TableList {...table.tableProps} />
      <output className="mt-4 block overflow-x-auto rounded-sm bg-muted p-3 text-xs">{request}</output>
    </div>
  );
}

const meta = {
  title: "Components/Table List/API v2",
  component: ApiV2Demo,
  tags: ["autodocs"],
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <NuqsAdapter>
        <I18nRoot namespaces={["common"]}>
          <Story />
        </I18nRoot>
      </NuqsAdapter>
    ),
  ],
} satisfies Meta<typeof ApiV2Demo>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StandardFetcher: Story = {};
