import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, Outlet, useParams, useRouterState } from "@tanstack/react-router";
import { ArrowLeftIcon, PencilIcon } from "lucide-react";
import {
  createContext,
  type Dispatch,
  type ReactNode,
  type RefObject,
  type SetStateAction,
  Suspense,
  useContext,
  useId,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import {
  ApiV2ProblemError,
  type BookingDetails,
  BookingUnavailableError,
  fetchBooking,
} from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { DeleteBookingDialog } from "./DeleteBookingDialog";

type BookingEventContextValue = {
  booking: BookingDetails;
  token: string;
  displayTimeZone: string;
  formId: string;
  editButtonRef: RefObject<HTMLAnchorElement | null>;
  announce: Dispatch<SetStateAction<string>>;
  setDirty: Dispatch<SetStateAction<boolean>>;
  refreshBooking: () => Promise<void>;
};

const BookingEventContext = createContext<BookingEventContextValue | null>(null);

export function useBookingEvent(): BookingEventContextValue {
  const value = useContext(BookingEventContext);
  if (!value) throw new Error("Booking event child rendered outside its page");
  return value;
}

export function Panel({
  as: Tag = "section",
  heading,
  headingId,
  headingRef,
  action,
  children,
}: {
  as?: "section" | "aside";
  heading: string;
  headingId: string;
  headingRef?: RefObject<HTMLHeadingElement | null>;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Tag aria-labelledby={headingId} className="min-w-0">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b pb-3">
        <h2 ref={headingRef} id={headingId} className="font-semibold" tabIndex={headingRef ? -1 : undefined}>
          {heading}
        </h2>
        {action}
      </div>
      <div className="pt-4 text-sm">{children}</div>
    </Tag>
  );
}

function formatDateTime(value: string, timeZone: string, language: string): string {
  return new Intl.DateTimeFormat(language, { dateStyle: "medium", timeStyle: "short", timeZone }).format(
    new Date(value),
  );
}

function BookingMetadataAside({ booking, displayTimeZone }: { booking: BookingDetails; displayTimeZone: string }) {
  const { t, i18n } = useTranslation("booking");
  return (
    <Panel
      as="aside"
      heading={t(
        booking.kind === "MAINTENANCE" ? "bookings.details.aboutMaintenance" : "bookings.details.aboutBooking",
      )}
      headingId="booking-metadata-heading"
    >
      <dl data-slot="timestamps" className="space-y-3">
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.timesShownIn")}</dt>
          <dd>{displayTimeZone}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.created")}</dt>
          <dd>
            <time dateTime={booking.createdAt}>
              {formatDateTime(booking.createdAt, displayTimeZone, i18n.language)}
            </time>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("bookings.details.lastUpdated")}</dt>
          <dd>
            <time dateTime={booking.updatedAt}>
              {formatDateTime(booking.updatedAt, displayTimeZone, i18n.language)}
            </time>
          </dd>
        </div>
      </dl>
    </Panel>
  );
}

const eventPageClassName = "@container mx-auto max-w-5xl space-y-6 p-4 sm:p-8";
const eventColumnsClassName = "grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_16rem]";

function BookingEventSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className={eventPageClassName} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-14 w-full" />
        <div className={eventColumnsClassName}>
          <div className="space-y-4">
            <Skeleton className="h-9 w-full" />
            {[0, 1, 2, 3].map((row) => (
              <Skeleton key={row} className="h-8 w-full" />
            ))}
          </div>
          <div className="space-y-4">
            <Skeleton className="h-9 w-full" />
            {[0, 1, 2].map((row) => (
              <Skeleton key={row} className="h-12 w-full" />
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}

export function BookingEventPage() {
  return (
    <Suspense fallback={<BookingEventSkeleton />}>
      <BookingEventContent />
    </Suspense>
  );
}

function BookingEventContent() {
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
  const editing = pathname.endsWith("/edit");
  const refreshBooking = async () => {
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
  };
  const eventName = document.target.value.name;
  const period = formatAgendaPeriod(document.start, document.end, preferences.timeZone);

  return (
    <main className={eventPageClassName}>
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
        <div className={eventColumnsClassName}>
          <Outlet />
          <BookingMetadataAside booking={document} displayTimeZone={preferences.timeZone} />
        </div>
      </BookingEventContext.Provider>

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </main>
  );
}

export function BookingDetailsView() {
  const { t, i18n } = useTranslation("booking");
  const { booking, displayTimeZone, editButtonRef } = useBookingEvent();
  const facts: Array<[string, ReactNode]> = [
    [
      t("bookings.details.when"),
      <span key="when">
        <time dateTime={booking.start}>{formatDateTime(booking.start, displayTimeZone, i18n.language)}</time>
        {" – "}
        <time dateTime={booking.end}>{formatDateTime(booking.end, displayTimeZone, i18n.language)}</time>
      </span>,
    ],
    ...(booking.kind === "BOOKING" && booking.bookedBy
      ? ([[t("bookings.details.bookedBy"), <UserBadge key="booked-by" name={booking.bookedBy} />]] as Array<
          [string, ReactNode]
        >)
      : []),
    ...(booking.createdBy
      ? ([[t("bookings.details.createdBy"), <UserBadge key="created-by" name={booking.createdBy} />]] as Array<
          [string, ReactNode]
        >)
      : []),
    [
      t(booking.kind === "MAINTENANCE" ? "bookings.form.notes" : "bookings.form.purpose"),
      booking.purpose ? (
        <span key="purpose" className="whitespace-pre-line">
          {booking.purpose}
        </span>
      ) : (
        <span key="purpose" className="text-muted-foreground">
          {t("bookings.details.noneProvided")}
        </span>
      ),
    ],
  ];

  return (
    <Panel
      heading={t(booking.kind === "MAINTENANCE" ? "bookings.details.maintenanceTitle" : "bookings.details.title")}
      headingId="booking-details-heading"
      action={
        booking.canEdit && booking.state === "CONFIRMED" ? (
          <Link
            ref={editButtonRef}
            className={buttonVariants({ size: "xs", variant: "ghost" })}
            to="/booking/calendar/bookings/$id/edit"
            params={{ id: String(booking.id) }}
            replace
            resetScroll={false}
          >
            <PencilIcon aria-hidden="true" />
            {t("bookings.actions.edit")}
          </Link>
        ) : null
      }
    >
      <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
        <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
          {facts.map(([label, value]) => (
            <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
              <dt className="font-medium">{label}</dt>
              <dd className="min-w-0">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Panel>
  );
}
