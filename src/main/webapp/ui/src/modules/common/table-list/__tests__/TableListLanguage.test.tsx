import { fakeCollectionResource } from "./fixtures/apiV2Resources";

const resource = {
  ...fakeCollectionResource("language-records"),
  runtimeFields: fakeCollectionResource("instruments").runtimeFields,
};

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createInstance } from "i18next";
import ICU from "i18next-icu";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import * as v from "valibot";
import { expect, it, vi } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { server } from "@/__tests__/mswServer";
import type { CollectionConfig } from "@/modules/common/collection/config";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import type { ApiV2CollectionAdapter } from "../api-v2/adapter";
import type { ApiV2CollectionMetadata } from "../api-v2/metadata";
import { TableListFilters } from "../components/TableListFilters";
import { TableListErrorState } from "../components/TableListStates";
import { useApiV2TableList } from "../hooks/apiV2TableList";
import { TableList } from "../TableList";
import { RuntimeFieldProjectionLimitError } from "../utils/errors";
import { definition } from "./fixtures/tableList";

type Document = Record<string, unknown>;
const documentSchema = v.object({
  id: v.number(),
  name: v.string(),
  target: v.object({ relationTo: v.literal("instruments"), value: v.object({ id: v.number() }) }),
  room: v.object({ relationTo: v.literal("instruments"), value: v.object({ id: v.number() }) }),
});
const config: CollectionConfig<Document, "id", "name"> = {
  slug: "language-records",
  idField: "id",
  useAsTitle: "name",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  defaultColumns: ["name", "target.name", "target.floor", "target.deleted"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "number", list: false },
    { name: "name", labelKey: "tableList.examples.fields.title", type: "text" },
    ...["target", "room"].map((name) => ({
      name,
      labelKey: "tableList.examples.fields.owner",
      type: "relationship" as const,
      relationTo: "instruments",
      hasMany: false,
    })),
  ],
};
const metadata: ApiV2CollectionMetadata<Document> = {
  resourceName: "language-records",
  fields: ["id", "name", "target", "room"],
  sorting: { fields: [], default: [], maximumFields: 5 },
  filtering: {
    selectors: {
      "target.name": { operators: ["=="], wildcards: false, fieldType: "text" },
      "room.name": { operators: ["=="], wildcards: false, fieldType: "text" },
      "target.floor": { operators: ["=="], wildcards: false, fieldType: "number", title: "Floor" },
      "target.deleted": { operators: ["=="], wildcards: false, fieldType: "boolean" },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 20, maximumLimit: 100 },
  runtimeFields: [
    {
      namespace: "customFields",
      catalog: "/api/v2/instruments/fields/customFields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
    },
  ],
};

async function languages() {
  const i18n = createInstance();
  const french = structuredClone(commonEnglish);
  french.tableList.examples.fields.owner = "Propriétaire";
  french.tableList.targetFields.name = "Nom";
  french.tableList.fields.hierarchicalLabel = "{owner} / {field}";
  french.tableList.fields.labelWithSource = "{label} de {source}";
  french.actions.yes = "Oui";
  french.tableList.error.customFieldColumnLimit = "Maximum {limit, number} champs";
  await i18n.use(ICU).init({
    lng: "en-US",
    fallbackLng: "en-US",
    defaultNS: "common",
    resources: { "en-US": { common: commonEnglish }, "fr-FR": { common: french } },
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

it("updates derived labels, values and separate owner groups without rebuilding the adapter or refetching", async () => {
  const user = userEvent.setup();
  const i18n = await languages();
  let records = 0;
  let catalogs = 0;
  server.use(
    http.get("/api/v2/language-records", ({ request }) => {
      records++;
      return HttpResponse.json(
        v2ListEnvelope(
          [
            {
              id: 1,
              name: "Microscope",
              target: { relationTo: "instruments", value: { id: 1234, name: "Lens", floor: 1234.5, deleted: true } },
              room: { relationTo: "instruments", value: { id: 2, name: "Lab" } },
              customFields: { SF104: 9876.5 },
            },
          ],
          new URL(request.url).searchParams.get("totals") === "true",
        ),
      );
    }),
    http.get("/api/v2/instruments/fields/customFields", () => {
      catalogs++;
      return HttpResponse.json({
        fields: [definition({ label: "Weight", type: "number", jsonType: "number" })],
        hasMore: false,
        page: 1,
        limit: 50,
      });
    }),
  );
  const adapters = new Set<ApiV2CollectionAdapter<Document>>();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Harness() {
    useTranslation();
    const table = useApiV2TableList({
      resource,
      config,
      documentSchema,
      metadata,
      table: {
        queryString: false,
        initialState: { visibleFields: [...config.defaultColumns, "customFields.SF104"] },
        features: { filtering: false, sorting: false, columns: true, pagination: false },
      },
    });
    adapters.add(table.adapter);
    return (
      <>
        <TableList {...table.tableProps} />
        <TableListFilters
          config={table.adapter.config}
          expression={{ kind: "comparison", field: "target.name", operator: "equals", value: "Lens" }}
          onApply={vi.fn()}
          onClose={vi.fn()}
        />
      </>
    );
  }
  render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>
        <Suspense fallback={null}>
          <Harness />
        </Suspense>
      </QueryClientProvider>
    </I18nextProvider>,
  );
  expect(await screen.findByRole("cell", { name: "1,234.5" })).toBeVisible();
  expect(screen.getByRole("cell", { name: "9,876.5" })).toBeVisible();
  expect(screen.getByRole("cell", { name: "Yes" })).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "Owner → Name" })).toBeVisible();
  expect(records).toBeGreaterThan(0);
  expect(catalogs).toBe(1);
  const before = { records, catalogs };
  const adapter = [...adapters].at(-1);
  adapters.clear();
  await user.click(screen.getByRole("combobox", { name: "Field for filter 1" }));
  expect(screen.getAllByRole("group", { name: "Owner" })).toHaveLength(2);

  await act(() => i18n.changeLanguage("fr-FR"));

  const groups = screen.getAllByRole("group", { name: "Propriétaire" });
  expect(groups).toHaveLength(2);
  for (const group of groups) expect(within(group).getByRole("option", { name: "Propriétaire / Nom" })).toBeVisible();
  await user.keyboard("{Escape}");
  expect(screen.getByRole("columnheader", { name: "Propriétaire / Nom" })).toBeVisible();
  expect(screen.getByRole("cell", { name: /1.234,5/ })).toBeVisible();
  expect(screen.getByRole("cell", { name: /9.876,5/ })).toBeVisible();
  expect(screen.getByRole("cell", { name: "Oui" })).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "Weight" })).toBeVisible();
  expect([...adapters]).toEqual([adapter]);
  expect({ records, catalogs }).toEqual(before);
});

it("retranslates an already-visible projection-limit error", async () => {
  const i18n = await languages();
  const error = new RuntimeFieldProjectionLimitError(2);
  render(
    <I18nextProvider i18n={i18n}>
      <TableListErrorState error={error} />
    </I18nextProvider>,
  );
  expect(
    screen.getByText(commonEnglish.tableList.error.customFieldColumnLimit.replace("{limit, number}", "2")),
  ).toBeVisible();
  await act(() => i18n.changeLanguage("fr-FR"));
  expect(screen.getByText("Maximum 2 champs")).toBeVisible();
});
