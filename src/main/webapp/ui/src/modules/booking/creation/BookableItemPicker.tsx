import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import { type BookableItemOption, bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { fetchBookingConfigurationByTarget } from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { Button } from "@/modules/common/ui/button";
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/modules/common/ui/combobox";
import { Label } from "@/modules/common/ui/label";

export async function loadBookableItems(
  search: { term?: string; target?: string; page?: number },
  token: string,
  signal: AbortSignal,
): Promise<{ options: readonly BookableItemOption[]; totalPages: number }> {
  const result = await fetchBookingCatalogue(
    { q: search.term, target: search.target, page: search.page, pageSize: 20 },
    token,
    signal,
  );
  return {
    options: result.items.map((item) => ({
      configurationId: item.configurationId,
      targetId: item.targetId,
      globalId: item.globalId,
      name: item.name,
      timezone: item.timezone,
      slotGranularityMinutes: item.slotGranularityMinutes,
      openingStart: item.openingStart,
      openingEnd: item.openingEnd,
      bufferBeforeMinutes: item.bufferBeforeMinutes,
      bufferAfterMinutes: item.bufferAfterMinutes,
      maxBookingDurationMinutes: item.maxBookingDurationMinutes,
      allowDoubleBooking: item.allowDoubleBooking,
    })),
    totalPages: Math.max(1, Math.ceil(result.total / result.pageSize)),
  };
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
    queryFn: ({ signal }) => fetchBookingConfigurationByTarget(target ?? "", token, signal),
    select: (configuration) => bookableItemOption(configuration),
    retry: false,
  });
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
  const [page, setPage] = useState(1);
  const setSearchTerm = useDebounce((value: string) => {
    setPage(1);
    setTerm(value);
  }, 250);
  const query = useQuery({
    queryKey: ["api-v2", "booking-catalogue", "picker", term.trim(), page],
    enabled: Boolean(token) && !disabled,
    queryFn: ({ signal }) => loadBookableItems({ term, page }, token, signal),
  });
  return (
    <div className="space-y-2">
      <Label htmlFor="booking-item-search">{t("bookings.form.item")}</Label>
      <Combobox
        items={query.data?.options ?? []}
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
            setPage(1);
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
          {query.data && query.data.totalPages > 1 ? (
            <div className="flex items-center justify-between gap-2 border-t p-2">
              <Button type="button" size="sm" variant="ghost" disabled={page === 1} onClick={() => setPage(page - 1)}>
                {t("bookings.form.previousItems")}
              </Button>
              <span className="text-xs text-muted-foreground">
                {t("bookings.form.itemPage", { page, total: query.data.totalPages })}
              </span>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={page === query.data.totalPages}
                onClick={() => setPage(page + 1)}
              >
                {t("bookings.form.nextItems")}
              </Button>
            </div>
          ) : null}
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
