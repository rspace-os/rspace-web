import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import useDebounce from "@/hooks/ui/useDebounce";
import { type BookableItemOption, bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { fetchBookingCatalogue, mapBookingCataloguePage } from "@/modules/booking/domain/bookingCatalogue";
import { mapBookingConfiguration } from "@/modules/booking/pages/bookable-items/bookingConfigurationSchema";
import { getSearchBookingCatalogueQueryOptions } from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { getListBookingConfigurationsQueryOptions } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { BookingsReadKind } from "@/modules/common/api/v2/generated/models/bookingsReadKind";
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

export async function loadBookableItems(
  search: { term?: string; target?: string; page?: number; eventKind?: BookingsReadKind },

  signal: AbortSignal,
): Promise<{ options: readonly BookableItemOption[]; totalPages: number }> {
  const result = await fetchBookingCatalogue(
    {
      q: search.term,
      target: search.target,
      capability: search.eventKind === "MAINTENANCE" ? "CREATE_BLOCKOUT" : "CREATE_BOOKING",
      page: search.page,
      pageSize: 20,
    },

    signal,
  );
  return pickerPage(result, search.eventKind);
}

function pickerPage(
  result: ReturnType<typeof mapBookingCataloguePage>,
  eventKind?: BookingsReadKind,
): { options: readonly BookableItemOption[]; totalPages: number } {
  return {
    options: result.items
      .filter((item) =>
        eventKind === "MAINTENANCE" ? item.capabilities.canCreateBlockout : item.capabilities.canCreateBooking,
      )
      .map((item) => ({
        configurationId: item.configurationId,
        targetId: item.targetId,
        globalId: item.globalId,
        name: item.name,
        timezone: item.timezone,
        slotGranularityMinutes: item.slotGranularityMinutes,
        incrementUnit: item.incrementUnit,
        openingHours: item.openingHours,
        bufferBeforeMinutes: item.bufferBeforeMinutes,
        bufferAfterMinutes: item.bufferAfterMinutes,
        maxBookingDurationMinutes: item.maxBookingDurationMinutes,
        allowDoubleBooking: item.allowDoubleBooking,
        capabilities: item.capabilities,
      })),
    totalPages: Math.max(1, Math.ceil(result.total / result.pageSize)),
  };
}
function pickerParameters(search: { term?: string; target?: string; page?: number; eventKind?: BookingsReadKind }) {
  return {
    q: search.term,
    target: search.target,
    capability: search.eventKind === "MAINTENANCE" ? ("CREATE_BLOCKOUT" as const) : ("CREATE_BOOKING" as const),
    mine: false,
    page: search.page ?? 1,
    limit: 20,
  };
}

export function useBookableItem(target: string | undefined) {
  return useQuery(
    getSearchBookingCatalogueQueryOptions(pickerParameters({ target }), {
      query: {
        enabled: target !== undefined,
        retry: false,
        select: (result) =>
          pickerPage(mapBookingCataloguePage(result)).options.find((option) => option.globalId === target),
      },
    }),
  );
}

export function useBookableItemConfiguration(target: string | undefined) {
  return useQuery(
    getListBookingConfigurationsQueryOptions(
      { where: `target==${target ?? ""}`, depth: 2, limit: 2 },
      {
        query: {
          enabled: target !== undefined,
          retry: false,
          select: (result) => {
            if (result.docs.length !== 1) return undefined;
            return bookableItemOption(mapBookingConfiguration(result.docs[0]));
          },
        },
      },
    ),
  );
}

export function BookableItemPicker({
  value,
  onChange,

  disabled = false,
  eventKind = "BOOKING",
}: {
  value: BookableItemOption | undefined;
  onChange: (value: BookableItemOption | undefined) => void;

  disabled?: boolean;
  eventKind?: BookingsReadKind;
}) {
  const { t } = useTranslation("booking");
  const [term, setTerm] = useState("");
  const [page, setPage] = useState(1);
  const setSearchTerm = useDebounce((value: string) => {
    setPage(1);
    setTerm(value);
  }, 250);
  const query = useQuery(
    getSearchBookingCatalogueQueryOptions(pickerParameters({ term, page, eventKind }), {
      query: {
        enabled: !disabled,
        select: (result) => pickerPage(mapBookingCataloguePage(result), eventKind),
      },
    }),
  );
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
