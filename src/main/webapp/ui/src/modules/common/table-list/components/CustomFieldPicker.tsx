import { useQueries } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import type { RelationshipLabel, RuntimeNamespaceSummary } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import {
  mapRuntimeFieldCatalog,
  type RuntimeFieldDefinition,
  runtimeFieldCatalogNamespace,
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
import { useRuntimeFieldResources } from "../api-v2/runtimeFieldResources";

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
  const groups = new Map<string, RuntimeFieldSourceGroup>();
  for (const source of sources) {
    const separator = source.namespace.lastIndexOf(".");
    const key = separator < 0 ? "" : source.namespace.slice(0, separator);
    const current = groups.get(key);
    groups.set(key, {
      key,
      via: current?.via ?? source.via,
      sources: current ? [...current.sources, source] : [source],
    });
  }
  return [...groups.values()];
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
  authScope?: string | number;
  ariaLabel: string;
  chosenLabel?: string;
  chosenSelector?: string;
  known: readonly { namespace: string; definitions: readonly RuntimeFieldDefinition[] }[];
  onSelect: (namespace: string, definition: RuntimeFieldDefinition) => void;
  isDefinitionAvailable?: (definition: RuntimeFieldDefinition) => boolean;
}) {
  const { t } = useTranslation("common");
  const { fieldOptionLabel } = useFieldLabels();
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);
  const setSearchTerm = useDebounce(setTerm, 250);
  const narrowed = term.trim().length >= MINIMUM_TERM;

  const resourceFor = useRuntimeFieldResources();
  const searchable = sources.flatMap((source) => {
    const resource = resourceFor(source);
    return resource ? [{ source, resource }] : [];
  });
  const queries = useQueries({
    queries: searchable.map(({ source, resource }) => {
      const namespace = runtimeFieldCatalogNamespace(source.namespace);
      const params = { q: term.trim(), limit: SEARCH_LIMIT };
      return {
        queryKey: resource.runtimeFields.queryKey(...([namespace, params] as never[])),
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          resource.runtimeFields.request(...([namespace, params, { signal, redirect: "error" }] as never[])),
        select: (body: unknown) =>
          mapRuntimeFieldCatalog(body).fields.map((definition) => ({ namespace: source.namespace, definition })),
        enabled: open && narrowed,
        staleTime: 60_000,
      };
    }),
  });
  const query = {
    data: queries.flatMap((result) => result.data ?? []),
    isFetching: queries.some((result) => result.isFetching),
    isError: queries.some((result) => result.isError),
  };

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
      labelKey: definition.label,
      label: definition.label,
      origin: { sourceLabel: sourceLabel(definition) },
    });

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
          setTerm("");
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
          <ComboboxStatus>
            {query.isFetching
              ? t("tableList.filters.suggestions.loading")
              : query.isError
                ? t("tableList.filters.customField.searchFailed")
                : narrowed
                  ? null
                  : t("tableList.filters.suggestions.minimumLength", { count: MINIMUM_TERM })}
          </ComboboxStatus>
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
