import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import {
  Autocomplete,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxStatus,
} from "@/modules/common/ui/combobox";

export type ValueSuggestions = {
  values: readonly string[];
  loading: boolean;
  failed: boolean;
  hasMore: boolean;
};

export function SuggestedValueInput({
  ariaLabel,
  value,
  onChange,
  useSuggestions,
  minimumLength = 0,
  placeholder,
}: {
  ariaLabel: string;
  value: string;
  onChange: (value: string) => void;
  useSuggestions: (term: string, open: boolean) => ValueSuggestions;
  minimumLength?: number;
  placeholder?: string;
}) {
  const { t } = useTranslation("common");
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [term, setTerm] = useState(value);
  const setSearchTerm = useDebounce(setTerm, 250);
  const belowMinimum = term.trim().length < minimumLength;
  const { values, loading, failed, hasMore } = useSuggestions(term.trim(), open && !belowMinimum);

  return (
    <div className="min-w-0">
      <Autocomplete
        items={values as string[]}
        filter={null}
        open={open}
        onOpenChange={setOpen}
        value={value}
        onValueChange={(next) => {
          onChange(next);
          setSearchTerm(next);
        }}
        openOnInputClick
      >
        <ComboboxInput
          showTrigger={false}
          aria-label={ariaLabel}
          aria-describedby={hasMore ? listId : undefined}
          aria-busy={loading || undefined}
          className="h-8 rounded-sm text-xs"
          placeholder={placeholder}
        />
        <ComboboxContent>
          <ComboboxStatus>
            {loading
              ? t("tableList.filters.suggestions.loading")
              : failed
                ? t("tableList.filters.suggestions.unavailable")
                : belowMinimum
                  ? t("tableList.filters.suggestions.minimumLength", { count: minimumLength })
                  : null}
          </ComboboxStatus>
          <ComboboxEmpty>
            {loading || failed || belowMinimum ? null : t("tableList.filters.suggestions.noMatch")}
          </ComboboxEmpty>
          <ComboboxList>
            {(item: string) => (
              <ComboboxItem key={item} value={item} title={item} className="truncate text-xs">
                {item}
              </ComboboxItem>
            )}
          </ComboboxList>
        </ComboboxContent>
      </Autocomplete>
      {hasMore ? (
        <p id={listId} className="mt-1 truncate text-[10px] text-muted-foreground">
          {t("tableList.filters.suggestions.truncated")}
        </p>
      ) : null}
    </div>
  );
}
