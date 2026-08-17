import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NuqsAdapter } from "nuqs/adapters/react";
import * as React from "react";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import type { ApiV2CollectionMetadata } from "./adapters/apiV2/apiV2CollectionMetadata";
import { useApiV2TableList } from "./adapters/apiV2/useApiV2TableList";
import { TableList } from "./TableList";

type BookingConfiguration = {
  id: string;
  timeZone: string;
  target: string;
};

const documentSchema = v.object({
  id: v.string(),
  timeZone: v.string(),
  target: v.string(),
});

const config = resolveCollectionConfig<BookingConfiguration>({
  slug: "bookingConfigurations",
  idField: "id",
  labels: {
    singularKey: "tableList.examples.record",
    pluralKey: "tableList.examples.records",
  },
  useAsTitle: "timeZone",
  defaultColumns: ["timeZone"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    { name: "timeZone", labelKey: "tableList.examples.fields.title", type: "text" },
    {
      // Declared once. The adapter derives target.name from the published selectors.
      name: "target",
      labelKey: "tableList.examples.fields.owner",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
    },
  ],
});

/** Mirrors what the generated API V2 OpenAPI document publishes for this collection. */
const metadata: ApiV2CollectionMetadata<BookingConfiguration> = {
  resourceName: "bookingConfigurations",
  fields: ["id", "timeZone", "target"],
  sorting: {
    fields: ["timeZone"],
    default: [{ field: "timeZone", direction: "asc" }],
    maximumFields: 5,
  },
  filtering: {
    selectors: {
      timeZone: { operators: ["==", "!=", "=contains="], wildcards: true },
      // Published by ApiV2OpenApiGenerator for each field of each relationship target. The
      // adapter turns this into a filter-only field, so the config above never names it.
      "target.name": {
        operators: ["==", "=contains=", "=like="],
        wildcards: true,
        title: "Bookable item name",
      },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 10, maximumLimit: 100 },
};

/**
 * Records the query the table sends, so a test can assert the RSQL a user's filter produces
 * without a running backend.
 */
function Demo() {
  const [lastQuery, setLastQuery] = React.useState("");
  const request = React.useMemo(
    () => ({
      fetch: ((input: RequestInfo | URL) => {
        const url = new URL(String(input), "http://localhost");
        setLastQuery(decodeURIComponent(url.search));
        return Promise.resolve(
          new Response(
            JSON.stringify({
              docs: [],
              totalDocs: 0,
              limit: 10,
              page: 1,
              pagingCounter: 1,
              totalPages: 0,
              hasPrevPage: false,
              hasNextPage: false,
              prevPage: null,
              nextPage: null,
            }),
            {
              status: 200,
              headers: { "content-type": "application/json" },
            },
          ),
        );
      }) as typeof globalThis.fetch,
    }),
    [],
  );
  const table = useApiV2TableList<BookingConfiguration>({
    resourceName: "bookingConfigurations",
    config,
    documentSchema,
    metadata,
    request,
    table: {
      // Off, so one test's filter does not survive in the URL into the next mount.
      queryString: false,
      features: { filtering: true, sorting: false, pagination: false, columns: false },
    },
  });

  return (
    <>
      <TableList {...table.tableProps} />
      <pre data-testid="last-query">{lastQuery}</pre>
    </>
  );
}

export function TargetFieldFilterStory() {
  const client = React.useMemo(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }), []);
  return (
    <QueryClientProvider client={client}>
      <NuqsAdapter>
        <React.Suspense fallback={null}>
          <Demo />
        </React.Suspense>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
