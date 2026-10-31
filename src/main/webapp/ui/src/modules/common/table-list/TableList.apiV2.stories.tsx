import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { NuqsAdapter } from "nuqs/adapters/react";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import type { ApiV2CollectionMetadata } from "./api-v2/metadata";
import { useApiV2TableList } from "./hooks/apiV2TableList";
import { TableList } from "./TableList";
import {
  researchRecordSchema as documentSchema,
  type ResearchRecord,
  storyConfig,
  storyRecords,
} from "./TableList.stories";

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

const storyFetch: typeof globalThis.fetch = async (input) => {
  const params = new URL(String(input), "http://localhost").searchParams;
  const page = Number(params.get("page") ?? 1);
  const limit = Number(params.get("limit") ?? 5);
  const total = storyRecords.length;
  return new Response(
    JSON.stringify({
      ...v2ListEnvelope(storyRecords.slice((page - 1) * limit, page * limit)),
      ...(params.get("totals") === "true" ? { totalDocs: total, totalPages: Math.ceil(total / limit) } : {}),
      limit,
      page,
      pagingCounter: (page - 1) * limit + 1,
      hasPrevPage: page > 1,
      prevPage: page > 1 ? page - 1 : null,
      hasNextPage: page * limit < total,
      nextPage: page * limit < total ? page + 1 : null,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
};

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
