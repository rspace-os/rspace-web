import { useQuery } from "@tanstack/react-query";
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
  authScope,
  ariaLabel,
  hint,
  chosenLabel,
  chosenSelector,
  known,
  onSelect,
  isDefinitionAvailable = () => true,
  fetchImpl,
}: {
  sources: readonly RuntimeNamespaceSummary[];
  authScope?: string | number;
  ariaLabel: string;
  hint?: string;
  chosenLabel: string | undefined;
  chosenSelector?: string;
  known: readonly { namespace: string; definitions: readonly RuntimeFieldDefinition[] }[];
  onSelect: (namespace: string, definition: RuntimeFieldDefinition) => void;
  isDefinitionAvailable?: (definition: RuntimeFieldDefinition) => boolean;
  fetchImpl?: typeof globalThis.fetch;
}) {
  const { t } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);
  const setSearchTerm = useDebounce(setTerm, 250);
  const narrowed = term.trim().length >= MINIMUM_TERM;

  const query = useQuery({
    queryKey: [
      "api-v2",
      "runtime-fields",
      "search",
      authScope ?? token,
      sources.map((source) => [source.namespace, source.catalog]),
      term.trim(),
    ],
    enabled: open && narrowed,
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
  const selected =
    [...loaded, ...results].find((item) => `${item.namespace}.${item.definition.id}` === chosenSelector) ?? null;
  const optionLabel = ({ definition }: RuntimeFieldOption) =>
    `${definition.label} (${definition.source.label || definition.type}${definition.source.label ? ` · ${definition.id}` : ""})`;

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
                  {item.definition.source.label === ""
                    ? item.definition.type
                    : `${item.definition.source.label} · ${item.definition.id}`}
                </span>
              </ComboboxItem>
            )}
          </ComboboxList>
        </ComboboxContent>
      </Combobox>
      {hint ? <p className="mt-1 text-[10px] text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
