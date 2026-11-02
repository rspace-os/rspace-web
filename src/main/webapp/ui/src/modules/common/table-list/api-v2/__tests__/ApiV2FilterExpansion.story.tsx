import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NuqsAdapter } from "nuqs/adapters/react";
import * as React from "react";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import {
  type BookingConfiguration,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { queryKeys } from "@/modules/common/hooks/auth";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import type { ApiV2CollectionMetadata } from "@/modules/common/table-list/api-v2/metadata";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { TableList } from "@/modules/common/table-list/TableList";

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
      "target.id": { operators: ["=="], wildcards: false, title: "Instrument ID", fieldType: "number" },
      "target.name": { operators: ["==", "=contains=", "=like="], wildcards: true, title: "Name", fieldType: "text" },
      "target.deleted": { operators: ["=="], wildcards: false, title: "Deleted", fieldType: "boolean" },
      "target.globalId": { operators: [], wildcards: false, title: "Global ID", fieldType: "text" },
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

function Demo({ narrow }: { narrow: boolean }) {
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationSchema,
    metadata: filterExpansionMetadata,
    request: { authScope: "api-v2-filter-expansion-story" },
    query: { retry: false },
    table: { queryString: { tableId: "api-v2-filter-expansion-story" } },
  });

  return (
    <div className={narrow ? "w-[360px] max-w-full" : undefined}>
      <TableList {...table.tableProps} />
    </div>
  );
}

export function ApiV2FilterExpansionStory({ narrow = false }: { narrow?: boolean } = {}) {
  const client = React.useMemo(() => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(queryKeys.oauthToken(true), "filter-expansion-story-token");
    return queryClient;
  }, []);

  return (
    <QueryClientProvider client={client}>
      <NuqsAdapter>
        <I18nRoot namespaces={["common", "booking"]}>
          <React.Suspense fallback={null}>
            <Demo narrow={narrow} />
          </React.Suspense>
        </I18nRoot>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}
