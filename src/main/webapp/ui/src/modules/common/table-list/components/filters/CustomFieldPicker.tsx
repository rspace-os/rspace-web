import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import type { RuntimeNamespaceSummary } from "@/modules/common/collection/collectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import {
  fetchRuntimeFieldCatalog,
  type RuntimeFieldDefinition,
} from "@/modules/common/table-list/adapters/apiV2/runtimeFieldCatalog";
import {
  Autocomplete,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxStatus,
} from "@/modules/common/ui/combobox";

const SEARCH_LIMIT = 20;
const MINIMUM_TERM = 2;

export type RuntimeFieldSourceGroup = {
  key: string;
  viaLabel: string;
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
      viaLabel: source.viaLabel,
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
  hint,
  chosenLabel,
  known,
  onSelect,
  isDefinitionAvailable = () => true,
  fetchImpl,
}: {
  sources: readonly RuntimeNamespaceSummary[];
  ariaLabel: string;
  hint?: string;
  chosenLabel: string | undefined;
  known: readonly { namespace: string; definitions: readonly RuntimeFieldDefinition[] }[];
  onSelect: (namespace: string, definition: RuntimeFieldDefinition) => void;
  isDefinitionAvailable?: (definition: RuntimeFieldDefinition) => boolean;
  fetchImpl?: typeof globalThis.fetch;
}) {
  const { t } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [input, setInput] = useState(chosenLabel ?? "");
  const [term, setTerm] = useState(chosenLabel ?? "");
  const [open, setOpen] = useState(false);
  const setSearchTerm = useDebounce(setTerm, 250);
  const narrowed = term.trim().length >= MINIMUM_TERM;

  const query = useQuery({
    queryKey: ["api-v2", "runtime-fields", "search", sources.map((source) => source.namespace).join(","), term.trim()],
    enabled: open && narrowed,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    queryFn: async ({ signal }) => {
      const headers = new Headers();
      if (token) headers.set("Authorization", `Bearer ${token}`);
      const pages = await Promise.all(
        sources.map(async (source) => ({
          source,
          page: await fetchRuntimeFieldCatalog(
            { catalog: source.catalog },
            { search: term.trim(), limit: SEARCH_LIMIT, headers, signal, fetch: fetchImpl },
          ),
        })),
      );
      return pages.flatMap(({ source, page }) =>
        page.fields.map((definition) => ({ namespace: source.namespace, definition })),
      );
    },
  });

  const sourceNames = new Set(sources.map((source) => source.namespace));
  const loaded = known
    .filter(({ namespace }) => sourceNames.has(namespace))
    .flatMap(({ namespace, definitions }) => definitions.map((definition) => ({ namespace, definition })));
  const results = (narrowed ? (query.data ?? []) : loaded).filter(({ definition }) =>
    isDefinitionAvailable(definition),
  );

  return (
    <div className="min-w-0">
      <Autocomplete
        items={results}
        filter={null}
        open={open}
        onOpenChange={setOpen}
        value={input}
        onValueChange={(next) => {
          setInput(next);
          setSearchTerm(next);
        }}
        itemToStringValue={(item: RuntimeFieldOption) => item.definition.label}
        openOnInputClick
      >
        <ComboboxInput
          showTrigger={false}
          aria-label={ariaLabel}
          aria-busy={query.isFetching || undefined}
          className="h-8 rounded-sm text-xs"
          placeholder={t("tableList.filters.customField.searchPlaceholder")}
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
                onClick={() => onSelect(item.namespace, item.definition)}
              >
                <span className="block w-full truncate text-xs">{item.definition.label}</span>
                <span className="block w-full truncate text-[11px] text-muted-foreground">
                  {item.definition.source.label === ""
                    ? item.definition.id
                    : `${item.definition.source.label} · ${item.definition.id}`}
                </span>
              </ComboboxItem>
            )}
          </ComboboxList>
        </ComboboxContent>
      </Autocomplete>
      {hint ? <p className="mt-1 text-[10px] text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
