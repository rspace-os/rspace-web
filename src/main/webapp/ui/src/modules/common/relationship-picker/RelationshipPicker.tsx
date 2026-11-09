import { useDebouncer } from "@tanstack/react-pacer";
import { uniqBy } from "es-toolkit";
import { type FocusEventHandler, type Ref, useState } from "react";
import { useTranslation } from "react-i18next";
import type { RelationshipOptionAvailabilitySource } from "@/modules/common/relationship-picker/types";
import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
  useComboboxAnchor,
} from "@/modules/common/ui/combobox";
import { Spinner } from "@/modules/common/ui/spinner";
import { cn } from "@/modules/common/utils/cn";
import { useRelationshipOptionAvailability, useRelationshipOptions, useSelectedRelationshipOptions } from "./queries";
import type { RelationshipOptionWithSource, RelationshipSource } from "./sources";

function splitValue(value: string, sources: readonly RelationshipSource[]): readonly string[] {
  const items = value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  return uniqBy(items, (item) => {
    const owner = sources.find((candidate) => candidate.ownsValue(item));
    return JSON.stringify([owner?.id ?? "unknown", owner?.normalizeValue?.(item) ?? item]);
  });
}

function sameOption(option: RelationshipOptionWithSource, other: RelationshipOptionWithSource) {
  return option.sourceId === other.sourceId && option.value === other.value;
}

/**
 * Selects related entities from one or more source adapters, by name or by source-owned value.
 *
 * The value is the comma-separated global ID list the REST API v2 relationship filter accepts, so
 * a caller stores and restores exactly what it sends.
 */
export function RelationshipPicker({
  sources,
  availabilitySource,
  authScope,
  value,
  onChange,
  onOptionChange,
  multiple = false,
  compact = false,
  disabled = false,
  id,
  name,
  required,
  autoFocus,
  ariaDescribedBy,
  ariaInvalid,
  ariaLabel,
  className,
  inputRef,
  onFocus,
  onBlur,
  showClear = true,
  browseWhenEmpty = false,
  browseEmptyMessage,
  minimumSearchLength,
  searchDebounceMs = 0,
}: {
  /** Sources searched in parallel and merged in declaration order. */
  sources: readonly RelationshipSource[];
  availabilitySource?: RelationshipOptionAvailabilitySource;
  /** Caller identity used to partition cached options when permissions differ between callers. */
  authScope?: string | number;
  value: string;
  onChange: (value: string) => void;
  /** Receives the selected option for consumers that need its display metadata. */
  onOptionChange?: (option: RelationshipOptionWithSource | null) => void;
  multiple?: boolean;
  /** Shrinks the control to the h-8 / text-xs sizing a filter row uses, and lets it shrink to its column. */
  compact?: boolean;
  disabled?: boolean;
  id?: string;
  name?: string;
  required?: boolean;
  autoFocus?: boolean;
  ariaDescribedBy?: string;
  ariaInvalid?: boolean;
  ariaLabel: string;
  className?: string;
  inputRef?: Ref<HTMLInputElement>;
  onFocus?: FocusEventHandler<HTMLInputElement>;
  onBlur?: FocusEventHandler<HTMLInputElement>;
  showClear?: boolean;
  /**
   * Loads options for an empty term too, so the list can be browsed before typing. Each source's
   * `search` then receives `""` and decides what an unfiltered page contains.
   */
  browseWhenEmpty?: boolean;
  /** Replaces the generic empty text when browsing with an empty term finds no options. */
  browseEmptyMessage?: string;
  /** The shortest term the sources search; a shorter one asks for more characters instead of reporting no matches. */
  minimumSearchLength?: number;
  searchDebounceMs?: number;
}) {
  const { t } = useTranslation("common");
  const anchor = useComboboxAnchor();
  const [term, setTerm] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const searchDebouncer = useDebouncer(setSearchTerm, { wait: searchDebounceMs });
  const debouncing = searchDebounceMs > 0 && searchTerm !== term;
  const trimmedTerm = term.trim();
  const hasSearchTerm = trimmedTerm !== "";
  const searchTooShort = hasSearchTerm && minimumSearchLength !== undefined && trimmedTerm.length < minimumSearchLength;
  const showsOptions = hasSearchTerm || browseWhenEmpty;
  const labels = {
    unavailableLabel: (value: string) => t("relationshipPicker.unavailable", { value }),
    failedLabel: (value: string) => t("relationshipPicker.restoreFailed", { value }),
    compact,
  };

  const selected = useSelectedRelationshipOptions({
    sources,
    values: splitValue(value, sources),
    authScope,
    labels,
  });
  const { options, failed, loading } = useRelationshipOptions({
    sources,
    term: searchDebounceMs > 0 ? searchTerm : term,
    authScope,
    labels,
    enabled: showsOptions,
  });
  const availability = useRelationshipOptionAvailability({
    sourceId: sources[0]?.id ?? "none",
    options,
    availabilitySource,
    authScope,
  });
  // Keeps the selected options selectable while a fresh search is in flight, so a chip never
  // disappears from the list mid-typing.
  const items = showsOptions && !debouncing ? [...options] : [];
  for (const option of selected) {
    if (!items.some((candidate) => sameOption(candidate, option))) items.push(option);
  }
  let emptyMessage: string;
  if (!showsOptions) {
    emptyMessage = t("relationshipPicker.enterSearchTerm");
  } else if (failed) {
    emptyMessage = t("relationshipPicker.failed");
  } else if (!hasSearchTerm && browseEmptyMessage !== undefined) {
    emptyMessage = browseEmptyMessage;
  } else if (searchTooShort) {
    emptyMessage = t("relationshipPicker.searchTooShort", { count: minimumSearchLength });
  } else {
    emptyMessage = t("relationshipPicker.empty");
  }
  const handleInputValueChange = (nextTerm: string, { reason }: { reason: string }) => {
    const isClearAction = reason === "input-clear" || reason === "clear-press" || reason === "item-press";
    if (reason !== "input-change" && !isClearAction) return;

    const updatedTerm = isClearAction ? "" : nextTerm;
    setTerm(updatedTerm);
    if (searchDebounceMs > 0) searchDebouncer.maybeExecute(updatedTerm);
  };
  const unavailableStatus = (option: RelationshipOptionWithSource) => availability.unavailable[String(option.value)];
  const optionIsDisabled = (option: RelationshipOptionWithSource) =>
    availabilitySource !== undefined &&
    (availability.checking || availability.failed || unavailableStatus(option) !== undefined);
  const availabilityDetails = (option: RelationshipOptionWithSource) => {
    if (availability.checking) return t("relationshipPicker.availabilityChecking");
    if (availability.failed) return t("relationshipPicker.availabilityFailed");
    const status = unavailableStatus(option);
    if (status === undefined) return null;
    return availabilitySource?.renderUnavailable(option, status);
  };
  const optionContent = (option: RelationshipOptionWithSource) => {
    const details = availabilityDetails(option);
    return (
      <div className="min-w-0 flex-1">
        {option.content ?? option.label}
        {details === null ? null : <div className="mt-1 text-xs text-muted-foreground">{details}</div>}
      </div>
    );
  };
  const optionsAreLoading = loading && showsOptions;
  const optionList = (
    <>
      {optionsAreLoading ? (
        <div role="status" className="px-2 py-1 text-xs text-muted-foreground">
          {t("loading")}
        </div>
      ) : null}
      <ComboboxList>
        {(option: RelationshipOptionWithSource) => (
          <ComboboxItem
            key={`${option.sourceId}:${option.value}`}
            value={option}
            disabled={optionIsDisabled(option)}
            className="py-1.5 pr-7 pl-2"
          >
            {optionContent(option)}
          </ComboboxItem>
        )}
      </ComboboxList>
      <ComboboxEmpty>{optionsAreLoading ? null : emptyMessage}</ComboboxEmpty>
    </>
  );
  const availabilityActions =
    availabilitySource?.renderAction === undefined
      ? []
      : options.flatMap((option) => {
          const status = unavailableStatus(option);
          return status === undefined
            ? []
            : [
                <div key={`${option.sourceId}:${option.value}`} className="text-xs">
                  {availabilitySource.renderAction?.(option, status)}
                </div>,
              ];
        });
  const actionRegion =
    availabilityActions.length === 0 ? null : <div className="mb-2 space-y-1">{availabilityActions}</div>;

  const common = {
    id,
    name,
    disabled,
    required,
    autoFocus,
    "aria-describedby": ariaDescribedBy,
    "aria-invalid": ariaInvalid || undefined,
    "aria-label": ariaLabel,
    placeholder: t("relationshipPicker.search"),
    ref: inputRef,
    onFocus,
    onBlur,
  };

  if (!multiple) {
    return (
      <>
        {actionRegion}
        <Combobox<RelationshipOptionWithSource>
          items={items}
          filter={null}
          value={selected[0] ?? null}
          disabled={disabled}
          onInputValueChange={handleInputValueChange}
          isItemEqualToValue={sameOption}
          itemToStringLabel={(option: RelationshipOptionWithSource) => option.label}
          onValueChange={(option: RelationshipOptionWithSource | null) => {
            if (option === null) {
              onChange("");
              onOptionChange?.(null);
            } else if (!optionIsDisabled(option)) {
              onChange(String(option.value));
              onOptionChange?.(option);
            }
          }}
        >
          <ComboboxInput
            {...common}
            size={compact ? "sm" : "default"}
            className={cn(compact && "w-full", className)}
            clearLabel={t("relationshipPicker.clear")}
            showClear={showClear}
            triggerLabel={t("relationshipPicker.openOptions")}
          />
          <ComboboxContent>{optionList}</ComboboxContent>
        </Combobox>
      </>
    );
  }

  return (
    <>
      {actionRegion}
      <Combobox<RelationshipOptionWithSource, true>
        items={items}
        filter={null}
        multiple
        value={selected}
        disabled={disabled}
        onInputValueChange={handleInputValueChange}
        isItemEqualToValue={sameOption}
        itemToStringLabel={(option: RelationshipOptionWithSource) => option.label}
        onValueChange={(next: RelationshipOptionWithSource[]) => {
          if (!next.some(optionIsDisabled)) {
            onChange(next.map((option) => String(option.value)).join(","));
          }
        }}
      >
        <ComboboxChips
          ref={anchor}
          size={compact ? "sm" : "default"}
          className={cn(compact && "max-h-24 w-full overflow-y-auto", className)}
          aria-busy={selected.some((option) => option.restoreStatus === "loading") || undefined}
        >
          <ComboboxValue>
            {(shown: RelationshipOptionWithSource[] | null) =>
              // base-ui passes null while the multiple value is empty
              (shown ?? []).map((option) => (
                <ComboboxChip
                  key={`${option.sourceId}:${option.value}`}
                  aria-invalid={option.restoreStatus === "invalid" || undefined}
                  aria-busy={option.restoreStatus === "loading" || undefined}
                  removeLabel={t("relationshipPicker.remove", { item: option.label })}
                >
                  {option.label}
                  {option.restoreStatus === "loading" ? (
                    <>
                      <span className="sr-only">{t("loading")}</span>
                      <Spinner aria-hidden="true" className="size-3" />
                    </>
                  ) : null}
                </ComboboxChip>
              ))
            }
          </ComboboxValue>
          <ComboboxChipsInput {...common} size={compact ? "sm" : "default"} />
        </ComboboxChips>
        <ComboboxContent anchor={anchor}>{optionList}</ComboboxContent>
      </Combobox>
    </>
  );
}
