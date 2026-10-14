import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import { type BookableItemOption, bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingEventKind } from "@/modules/booking/domain/booking";
import { bookableItemsPickerQuery, enabledBookingPickerTargetQuery } from "@/modules/booking/domain/bookingCatalogue";
import { bookingConfigurationPickerTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
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
import { Skeleton } from "@/modules/common/ui/skeleton";

export { loadBookableItems } from "@/modules/booking/domain/bookingCatalogue";

export function useBookableItem(target: string | undefined, token: string) {
  return useQuery({
    ...enabledBookingPickerTargetQuery(target, token),
    enabled: Boolean(token),
    select: (page) => page.options.find((option) => option.globalId === target),
  });
}

export function useBookableItemConfiguration(target: string | undefined, token: string) {
  return useQuery({
    ...bookingConfigurationPickerTargetQuery(target, token),
    enabled: Boolean(token),
    select: (configuration) => bookableItemOption(configuration),
  });
}

export function BookableItemPicker({
  value,
  onChange,
  token,
  disabled = false,
  eventKind = "BOOKING",
}: {
  value: BookableItemOption | undefined;
  onChange: (value: BookableItemOption | undefined) => void;
  token: string;
  disabled?: boolean;
  eventKind?: BookingEventKind;
}) {
  const { t } = useTranslation("booking");
  const [term, setTerm] = useState("");
  const [page, setPage] = useState(1);
  const setSearchTerm = useDebounce((value: string) => {
    setPage(1);
    setTerm(value);
  }, 250);
  const query = useQuery({
    ...bookableItemsPickerQuery(term, page, eventKind, token),
    enabled: Boolean(token) && !disabled,
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
        itemToStringLabel={(option) =>
          t("bookings.form.selectedItem", { name: option.name, globalId: option.globalId })
        }
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
          {query.isLoading ? <Skeleton aria-hidden="true" className="m-2 h-16" /> : null}
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
      {query.isLoading ? (
        <p role="status" className="sr-only">
          {t("bookings.loadingConfiguration")}
        </p>
      ) : null}
    </div>
  );
}
