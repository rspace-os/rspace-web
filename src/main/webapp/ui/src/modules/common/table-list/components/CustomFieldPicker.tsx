import { useDebouncedState } from "@tanstack/react-pacer";
import { skipToken, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { RelationshipLabel, RuntimeNamespaceSummary } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import {
  mapRuntimeFieldSearch,
  type RuntimeFieldCatalogForNamespace,
  type RuntimeFieldDefinition,
  registeredRuntimeFieldSearches,
  runtimeFieldCatalogNamespace,
  soleRuntimeFieldSearch,
} from "@/modules/common/table-list/api-v2/runtimeFieldCatalog";
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxStatus,
} from "@/modules/common/ui/combobox";

const SEARCH_LIMIT = 20;
const MINIMUM_TERM = 2;

type RuntimeFieldSourceGroup = {
  key: string;
  via?: RelationshipLabel;
  sources: readonly RuntimeNamespaceSummary[];
};

export function groupRuntimeFieldSources(
  sources: readonly RuntimeNamespaceSummary[],
): readonly RuntimeFieldSourceGroup[] {
  const groups = Map.groupBy(sources, (source) =>
    source.namespace.slice(0, Math.max(source.namespace.lastIndexOf("."), 0)),
  );
  return [...groups].map(([key, group]) => ({
    key,
    via: group.find((source) => source.via !== undefined)?.via,
    sources: group,
  }));
}

type RuntimeFieldOption = {
  namespace: string;
  definition: RuntimeFieldDefinition;
};

export function CustomFieldPicker({
  sources,
  ariaLabel,
  chosenLabel,
  chosenSelector,
  known,
  onSelect,
  isDefinitionAvailable = () => true,
}: {
  sources: readonly RuntimeNamespaceSummary[];
  ariaLabel: string;
  chosenLabel?: string;
  chosenSelector?: string;
  known: readonly RuntimeFieldCatalogForNamespace[];
  onSelect: (namespace: string, definition: RuntimeFieldDefinition) => void;
  isDefinitionAvailable?: (definition: RuntimeFieldDefinition) => boolean;
}) {
  const { t } = useTranslation("common");
  const { fieldOptionLabel } = useFieldLabels();
  const [term, setSearchTerm, termDebouncer] = useDebouncedState("", { wait: 250 });
  const [open, setOpen] = useState(false);
  const narrowed = term.trim().length >= MINIMUM_TERM;

  const runtimeSearch = soleRuntimeFieldSearch(registeredRuntimeFieldSearches(sources));
  const params = { q: term.trim(), limit: SEARCH_LIMIT };
  // Not a suspense query, so a new term does not suspend the input.
  const query = useQuery({
    queryKey: runtimeSearch?.search.queryKey(params) ?? ["runtimeFieldSearch", "unavailable"],
    queryFn:
      runtimeSearch && open && narrowed
        ? ({ signal }) => runtimeSearch.search.request(params, { signal, redirect: "error" })
        : skipToken,
    select: (body): readonly RuntimeFieldOption[] => {
      if (!runtimeSearch) return [];
      const catalogs = mapRuntimeFieldSearch(body, runtimeSearch.search.namespaces);
      return runtimeSearch.sources.flatMap((source) =>
        (catalogs.get(runtimeFieldCatalogNamespace(source.namespace))?.fields ?? []).map((definition) => ({
          namespace: source.namespace,
          definition,
        })),
      );
    },
    staleTime: 60_000,
  });

  const sourceNames = new Set(sources.map((source) => source.namespace));
  const loaded = known
    .filter(({ namespace }) => sourceNames.has(namespace))
    .flatMap(({ namespace, definitions }) => definitions.map((definition) => ({ namespace, definition })));
  const results = (narrowed ? (query.data ?? []) : loaded).filter(({ definition }) =>
    isDefinitionAvailable(definition),
  );
  const selected =
    [...loaded, ...results].find((item) => `${item.namespace}.${item.definition.id}` === chosenSelector) ?? null;
  const typeLabels = {
    text: t("tableList.filters.customField.types.text"),
    number: t("tableList.filters.customField.types.number"),
    date: t("tableList.filters.customField.types.date"),
    time: t("tableList.filters.customField.types.time"),
    radio: t("tableList.filters.customField.types.radio"),
    choice: t("tableList.filters.customField.types.choice"),
  };
  const sourceLabel = (definition: RuntimeFieldDefinition) =>
    definition.source.label
      ? t("tableList.filters.customField.sourceAndId", { source: definition.source.label, id: definition.id })
      : typeLabels[definition.type];
  const optionLabel = ({ definition }: RuntimeFieldOption) =>
    fieldOptionLabel({
      label: definition.label,
      origin: { sourceLabel: sourceLabel(definition) },
    });
  const searchStatus = () => {
    if (query.isFetching) return t("tableList.filters.suggestions.loading");
    if (query.isError) return t("tableList.filters.customField.searchFailed");
    if (narrowed) return null;
    return t("tableList.filters.suggestions.minimumLength", { count: MINIMUM_TERM });
  };

  return (
    <div className="min-w-0">
      <Combobox
        items={results}
        filter={null}
        open={open}
        onOpenChange={setOpen}
        value={selected}
        onInputValueChange={(next, { reason }) => {
          if (reason === "input-change" || reason === "input-clear") setSearchTerm(next);
        }}
        onValueChange={(item: RuntimeFieldOption | null) => {
          if (item) onSelect(item.namespace, item.definition);
          setSearchTerm("");
          termDebouncer.flush();
        }}
        itemToStringLabel={optionLabel}
        isItemEqualToValue={(left, right) =>
          left.namespace === right.namespace && left.definition.id === right.definition.id
        }
      >
        <ComboboxInput
          triggerLabel={ariaLabel}
          aria-label={ariaLabel}
          aria-busy={query.isFetching || undefined}
          className="h-8 rounded-sm text-xs"
          placeholder={chosenLabel ?? t("tableList.filters.customField.searchPlaceholder")}
        />
        <ComboboxContent>
          <ComboboxStatus>{searchStatus()}</ComboboxStatus>
          <ComboboxEmpty>
            {query.isFetching || query.isError ? null : t("tableList.filters.customField.noMatch")}
          </ComboboxEmpty>
          <ComboboxList>
            {(item: RuntimeFieldOption) => (
              <ComboboxItem
                key={`${item.namespace}:${item.definition.id}`}
                value={item}
                className="flex-col items-start gap-0"
              >
                <span className="block w-full truncate text-xs">{item.definition.label}</span>
                <span className="block w-full truncate text-[11px] text-muted-foreground">
                  {sourceLabel(item.definition)}
                </span>
              </ComboboxItem>
            )}
          </ComboboxList>
        </ComboboxContent>
      </Combobox>
    </div>
  );
}
