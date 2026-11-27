import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import * as React from "react";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import {
  type BookingConfiguration,
  bookingConfigurationConfig,
  mapBookingConfiguration,
} from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { apiV2Resources } from "@/modules/common/api/v2/generated/apiV2Resources";
import { BookingConfigurationsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import type { ApiV2CollectionMetadata } from "@/modules/common/table-list/api-v2/metadata";
import { usePersistedApiV2TableList } from "@/modules/common/table-list/hooks/useApiV2TableList";
import { tableViewDescriptor } from "@/modules/common/table-list/persistence";
import { TableList } from "@/modules/common/table-list/TableList";
import { tableViewRouteOptions } from "@/modules/common/table-list/tableViewRoute";

export const filterExpansionMetadata: ApiV2CollectionMetadata<BookingConfiguration> = {
  resourceName: "booking-configurations",
  fields: ["id", "target", "enabled", "state", "timezone", ...schedulingSettingsFieldNames, "updatedAt"],
  sorting: {
    fields: ["id", "enabled", "timezone", "updatedAt"],
    default: [{ field: "id", direction: "asc" }],
    maximumFields: 5,
  },
  filtering: {
    selectors: {
      id: { operators: ["==", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out="], wildcards: false },
      target: {
        operators: ["==", "=in="],
        wildcards: false,
        picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
      },
      enabled: { operators: ["==", "!=", "=out="], wildcards: false },
      state: { operators: ["==", "!=", "=in=", "=out="], wildcards: false },
      timezone: { operators: ["==", "!=", "=contains="], wildcards: true },
      updatedAt: { operators: ["==", "=gt=", "=lt="], wildcards: false },
      "target.id": { operators: ["=="], wildcards: false, fieldType: "number" },
      "target.name": { operators: ["==", "=contains=", "=like="], wildcards: true, fieldType: "text" },
      "target.deleted": { operators: ["=="], wildcards: false, fieldType: "boolean" },
      "target.globalId": { operators: [], wildcards: false, fieldType: "text" },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  runtimeFields: [
    {
      namespace: "target.extraFields",
      catalog: "/api/v2/instruments/fields/extraFields",
      responseField: "",
      filterable: true,
      columnSelectable: false,
      sortable: false,
      maximumProjections: 0,
      catalogDefaultLimit: 20,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 1,
      via: "target",
      viaResource: "instruments",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

export const filterExpansionTable = tableViewDescriptor(bookingConfigurationConfig, "issue", {
  tableId: "api-v2-filter-expansion-story",
});

function Demo({ narrow }: { narrow: boolean }) {
  const table = usePersistedApiV2TableList({
    persistence: filterExpansionTable,
    resource: apiV2Resources["booking-configurations"],
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationsReadSchema,
    mapDocument: mapBookingConfiguration,
    metadata: filterExpansionMetadata,
    request: { authScope: "api-v2-filter-expansion-story" },
    query: { retry: false },
  });

  return (
    <div className={narrow ? "w-[360px] max-w-full" : undefined}>
      <TableList {...table.tableProps} />
    </div>
  );
}

/** Renders on a memory router whose route saves and restores the table's view. */
export function ApiV2FilterExpansionStory({ narrow = false }: { narrow?: boolean } = {}) {
  const client = React.useMemo(() => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return queryClient;
  }, []);
  const [router] = React.useState(() =>
    createRouter({
      routeTree: createRootRoute({
        ...tableViewRouteOptions([filterExpansionTable]),
        component: () => (
          <MemoryHistoryNuqsAdapter>
            <I18nRoot namespaces={["common", "booking"]}>
              <React.Suspense fallback={null}>
                <Demo narrow={narrow} />
              </React.Suspense>
            </I18nRoot>
          </MemoryHistoryNuqsAdapter>
        ),
      }),
      history: createMemoryHistory({ initialEntries: ["/"] }),
    }),
  );

  return (
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
