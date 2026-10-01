import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, Outlet, useParams, useRouterState } from "@tanstack/react-router";
import { ArrowLeftIcon } from "lucide-react";
import { useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingCalendarFileButton } from "@/modules/booking/components/BookingCalendarFileButton";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import { ApiV2ProblemError, BookingUnavailableError, fetchBooking } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { detailColumnsClassName, detailPageClassName } from "../DetailPageShell";
import { BookingEventContext } from "./BookingEventContext";
import { BookingEventSkeleton } from "./BookingEventSkeleton";
import { BookingMetadataAside } from "./BookingMetadataAside";
import { DeleteBookingDialog } from "./DeleteBookingDialog";

const eventColumnsClassName = detailColumnsClassName;

export function BookingEventContent() {
  const { t } = useTranslation(["booking", "common"]);
  const { id } = useParams({ from: "/booking/calendar/bookings/$id" });
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const bookingId = Number(id);
  const validBookingId = Number.isSafeInteger(bookingId) && bookingId > 0;
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const queryClient = useQueryClient();
  const [announcement, announce] = useState("");
  const [dirty, setDirty] = useState(false);
  const editButtonRef = useRef<HTMLAnchorElement>(null);
  const stateBadgeRef = useRef<HTMLSpanElement>(null);
  const formId = `booking-event-${useId()}`;
  const booking = useQuery({
    queryKey: ["api-v2", "bookings", bookingId],
    enabled: validBookingId && Boolean(token),
    queryFn: ({ signal }) => fetchBooking(bookingId, token, signal),
    retry: (failureCount, error) => {
      if (error instanceof BookingUnavailableError) return false;
      if (error instanceof ApiV2ProblemError && error.status >= 400 && error.status < 500) return false;
      return failureCount < 2;
    },
  });
  const editing = pathname.endsWith("/edit");
  const bookingItem = useBookableItemConfiguration(
    editing && booking.data?.canEdit && booking.data.state === "CONFIRMED" ? booking.data.target.globalId : undefined,
    token,
  );

  const returnToMyBookings = (
    <Link className={buttonVariants({ variant: "outline" })} to="/booking/my-bookings" search={{ period: "upcoming" }}>
      {t("bookings.details.returnToMyBookings")}
    </Link>
  );

  if (!validBookingId || booking.isError || (!booking.isPending && !booking.data)) {
    const transportFailure =
      booking.isError &&
      !(booking.error instanceof BookingUnavailableError) &&
      !(booking.error instanceof ApiV2ProblemError && booking.error.status >= 400 && booking.error.status < 500);
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>
              {transportFailure ? t("bookings.details.loadFailedTitle") : t("bookings.details.unavailableTitle")}
            </EmptyTitle>
            <EmptyDescription>
              {transportFailure
                ? t("bookings.details.loadFailedDescription")
                : t("bookings.details.unavailableDescription")}
            </EmptyDescription>
          </EmptyHeader>
          <div className="flex gap-2">
            {returnToMyBookings}
            {transportFailure ? (
              <Button type="button" onClick={() => void booking.refetch()}>
                {t("common:actions.retry")}
              </Button>
            ) : null}
          </div>
        </Empty>
      </main>
    );
  }

  if (booking.isPending || !booking.data) {
    return <BookingEventSkeleton />;
  }

  const document = booking.data;
  const refreshBooking = async () => {
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
  };
  const eventName = document.target.value.name;
  const period = formatAgendaPeriod(document.start, document.end, preferences.timeZone);

  return (
    <main className={detailPageClassName}>
      <DirtyNavigationGuard dirty={dirty} />
      {document.canViewConfiguration ? (
        <Link
          className={buttonVariants({ variant: "ghost", size: "sm" })}
          to="/booking/bookable-items/$globalId/{-$tab}"
          params={{ globalId: document.target.globalId, tab: undefined }}
        >
          <ArrowLeftIcon aria-hidden="true" />
          {t("bookings.details.returnToItemCalendar")}
        </Link>
      ) : (
        returnToMyBookings
      )}

      <section className="flex flex-wrap items-center gap-4">
        <InventoryItem
          name={eventName}
          nameAs="h1"
          nameClassName="text-2xl font-semibold"
          globalId={document.target.globalId}
          idPlacement="title"
          className="min-w-full flex-1 p-0 sm:min-w-0"
          idLink={
            document.canViewConfiguration ? (
              <Link
                to="/booking/bookable-items/$globalId/{-$tab}"
                params={{ globalId: document.target.globalId, tab: undefined }}
                aria-label={t("bookings.details.viewItem", { globalId: document.target.globalId })}
              />
            ) : undefined
          }
        >
          {document.canViewConfiguration &&
          document.target.value.parentContainerName &&
          document.target.value.parentContainerGlobalId ? (
            <InventoryLocationLink
              name={document.target.value.parentContainerName}
              globalId={document.target.value.parentContainerGlobalId}
              compact
            />
          ) : null}
        </InventoryItem>
        <div
          data-slot="booking-event-header-actions"
          className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0 [&_[data-slot=badge]]:h-[30px] [&_button]:h-[30px] [&_button]:min-h-[30px]"
        >
          <Badge ref={stateBadgeRef} tabIndex={-1} variant={document.state === "CANCELLED" ? "destructive" : "default"}>
            {document.state === "CANCELLED" ? t("bookings.details.cancelled") : t("bookings.details.confirmed")}
          </Badge>
          {document.canViewConfiguration && document.state === "CONFIRMED" ? (
            <BookingCalendarFileButton bookingId={document.id} itemName={eventName} period={period} token={token} />
          ) : null}
          {!editing && document.canCancel && document.state === "CONFIRMED" ? (
            <DeleteBookingDialog
              bookingId={document.id}
              bookingVersion={document.version}
              itemName={eventName}
              period={period}
              token={token}
              eventKind={document.kind}
              onDeleted={() => {
                requestAnimationFrame(() => stateBadgeRef.current?.focus());
              }}
            />
          ) : null}
        </div>
      </section>

      {editing && bookingItem.data ? (
        <div className="@2xl:hidden">
          <BookingItemInformationCard as="section" item={bookingItem.data} displayTimezone={preferences.timeZone} />
        </div>
      ) : null}

      <BookingEventContext.Provider
        value={{
          booking: document,
          token,
          displayTimeZone: preferences.timeZone,
          formId,
          editButtonRef,
          announce,
          setDirty,
          refreshBooking,
        }}
      >
        <div className={editing ? "grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_24rem]" : eventColumnsClassName}>
          <Outlet />
          {editing && bookingItem.data ? (
            <div className="hidden @2xl:block">
              <BookingItemInformationCard item={bookingItem.data} displayTimezone={preferences.timeZone} />
            </div>
          ) : (
            <BookingMetadataAside booking={document} displayTimeZone={preferences.timeZone} />
          )}
        </div>
      </BookingEventContext.Provider>

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </main>
  );
}
