import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import useDebounce from "@/hooks/ui/useDebounce";
import { schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import { type BookableItemOption, bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsql } from "@/modules/common/table-list/adapters/apiV2/rsql/serializeRsql";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/modules/common/ui/combobox";
import { Label } from "@/modules/common/ui/label";

const DocumentSchema = v.object({
  id: v.number(),
  target: v.object({
    relationTo: v.literal("instruments"),
    globalId: v.string(),
    value: v.object({ id: v.number(), name: v.string(), deleted: v.boolean() }),
  }),
  timezone: v.string(),
  ...schedulingSettingsEntries,
});
const ListSchema = v2ListEnvelope(DocumentSchema);
const limits = {
  maximumComparisons: 2,
  maximumLikeComparisons: 1,
  maximumNesting: 2,
  maximumArguments: 20,
  maximumWhereLength: 2000,
};
const selectors = {
  enabled: { operators: ["=="] as const, wildcards: false },
  target: { operators: ["=="] as const, wildcards: false },
  "target.name": { operators: ["=contains="] as const, wildcards: false },
};
type PickerFilterDocument = { enabled: boolean; target: string; "target.name": string };

export async function loadBookableItems(
  search: { term?: string; target?: string; page?: number },
  token: string,
  signal: AbortSignal,
  includeDisabled = false,
): Promise<{ options: readonly BookableItemOption[]; totalPages: number }> {
  const userFilter: FilterExpression<PickerFilterDocument> | undefined = search.target
    ? { kind: "comparison" as const, field: "target", operator: "equals" as const, value: search.target }
    : search.term?.trim()
      ? {
          kind: "comparison" as const,
          field: "target.name",
          operator: "contains" as const,
          value: search.term.trim(),
        }
      : undefined;
  const expression: FilterExpression<PickerFilterDocument> =
    includeDisabled && userFilter
      ? userFilter
      : userFilter
        ? {
            kind: "and" as const,
            children: [
              { kind: "comparison" as const, field: "enabled", operator: "equals" as const, value: true },
              userFilter,
            ],
          }
        : { kind: "comparison" as const, field: "enabled", operator: "equals" as const, value: true };
  const params = new URLSearchParams({
    where: serializeRsql<PickerFilterDocument>(expression, selectors, limits),
    page: String(search.page ?? 1),
    limit: "20",
    depth: "1",
    "fields[booking-configurations]":
      "id,target,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking",
  });
  const response = await fetch(`/api/v2/booking-configurations?${params}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Bookable item request failed (${response.status})`);
  const page = parseOrThrow(ListSchema, await response.json());
  return { options: page.docs.map((document) => bookableItemOption(document)), totalPages: page.totalPages };
}

export function useBookableItem(target: string | undefined, token: string) {
  return useQuery({
    queryKey: ["api-v2", "booking-configurations", "enabled-picker", "target", target],
    enabled: Boolean(target && token),
    queryFn: ({ signal }) => loadBookableItems({ target }, token, signal),
    select: (page) => page.options.find((option) => option.globalId === target),
    retry: false,
  });
}

export function useBookableItemConfiguration(target: string | undefined, token: string) {
  return useQuery({
    queryKey: ["api-v2", "booking-configurations", "picker", "target", target],
    enabled: Boolean(target && token),
    queryFn: ({ signal }) => loadBookableItems({ target }, token, signal, true),
    select: (page) => page.options.find((option) => option.globalId === target),
    retry: false,
  });
}

async function loadAllBookableItems(term: string, token: string, signal: AbortSignal) {
  const first = await loadBookableItems({ term, page: 1 }, token, signal);
  const options = [...first.options];
  for (let page = 2; page <= first.totalPages; page += 1) {
    options.push(...(await loadBookableItems({ term, page }, token, signal)).options);
  }
  return options;
}

export function BookableItemPicker({
  value,
  onChange,
  token,
  disabled = false,
}: {
  value: BookableItemOption | undefined;
  onChange: (value: BookableItemOption | undefined) => void;
  token: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation("booking");
  const [term, setTerm] = useState("");
  const setSearchTerm = useDebounce(setTerm, 250);
  const query = useQuery({
    queryKey: ["api-v2", "booking-configurations", "enabled-picker", "search", term.trim()],
    enabled: Boolean(token),
    queryFn: ({ signal }) => loadAllBookableItems(term, token, signal),
  });
  return (
    <div className="space-y-2">
      <Label htmlFor="booking-item-search">{t("bookings.form.item")}</Label>
      <Combobox
        items={query.data ?? []}
        filter={null}
        value={value ?? null}
        disabled={disabled}
        isItemEqualToValue={(option, selected) => option.globalId === selected.globalId}
        itemToStringLabel={(option) => `${option.name} (${option.globalId})`}
        onInputValueChange={(nextTerm, { reason }) => {
          if (reason === "input-change") setSearchTerm(nextTerm);
          if (reason === "input-clear" || reason === "clear-press" || reason === "item-press") {
            setSearchTerm("");
            setTerm("");
          }
        }}
        onValueChange={(option) => onChange(option ?? undefined)}
      >
        <ComboboxInput
          id="booking-item-search"
          aria-label={t("bookings.form.item")}
          placeholder={t("bookings.form.itemSearch")}
          triggerLabel={t("bookings.form.itemChoose")}
          disabled={disabled}
        />
        <ComboboxContent>
          <ComboboxList>
            {(option: BookableItemOption) => (
              <ComboboxItem key={option.globalId} value={option} aria-label={t("bookings.form.itemOption", option)}>
                {t("bookings.form.itemOption", option)}
              </ComboboxItem>
            )}
          </ComboboxList>
          <ComboboxEmpty>
            {query.isPending ? null : query.isError ? t("bookings.errors.itemLoad") : t("bookings.form.itemNone")}
          </ComboboxEmpty>
        </ComboboxContent>
      </Combobox>
      {query.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">
          {t("bookings.loadingConfiguration")}
        </p>
      ) : null}
    </div>
  );
}
