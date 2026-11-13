import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarCheck2Icon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useBookableItemConfiguration } from "@/modules/booking/creation/BookableItemPicker";
import { BookingForm, type BookingFormSubmission, type EditableBooking } from "@/modules/booking/creation/BookingForm";
import { type BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingProblemKey, isBookingOutcomeUncertain } from "@/modules/booking/creation/useCreateBooking";
import { type BookingListDocument, isBookingOverlapError, updateBooking } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { invalidateListBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getUpdateBookingMutationOptions,
  invalidateGetBooking,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { type BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { TableList, type TableListProps } from "@/modules/common/table-list/TableList";
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { type BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { CalendarAgenda } from "./CalendarAgenda";
import { CalendarFilterControls } from "./CalendarFilterControls";
import { CalendarResourceSchedule, ResourceScheduleSkeleton } from "./CalendarResourceSchedule";
import { CalendarTimeGrid } from "./CalendarTimeGrid";
import { type BookingCalendarResource, type CalendarLayout, type CalendarView } from "./calendarLayoutUtils";

const bookingEventListConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "purpose", "bookedBy"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target",
      capabilities: { filterOperators: [] },
    },
    {
      name: "requester",
      type: "relationship",
      relationTo: "users",
      hasMany: false,
      labelKey: "booking:calendar.fields.requester",
      list: false,
      form: false,
      capabilities: { filterOperators: [] },
    },
    { name: "purpose", type: "text", maximumLength: 1_000, labelKey: "booking:calendar.fields.purpose" },
    { name: "bookedBy", type: "text", maximumLength: 255, labelKey: "booking:calendar.fields.bookedBy" },
    {
      name: "privacy",
      type: "select",
      options: ["full", "busy"],
      labelKey: "booking:calendar.fields.privacy",
    },
    { name: "timezone", type: "text", labelKey: "booking:calendar.fields.timezone" },
    { name: "start", type: "dateTime", labelKey: "booking:calendar.fields.start" },
    { name: "end", type: "dateTime", labelKey: "booking:calendar.fields.end" },
    { name: "state", type: "select", options: ["CONFIRMED", "CANCELLED"], labelKey: "booking:calendar.fields.state" },
    { name: "canEdit", type: "boolean", labelKey: "booking:calendar.fields.editable", list: false },
    { name: "createdAt", type: "dateTime", labelKey: "booking:calendar.fields.createdAt", list: false },
    { name: "updatedAt", type: "dateTime", labelKey: "booking:calendar.fields.updatedAt", list: false },
  ],
});

export const BookingEditorsContext = React.createContext<
  | {
      setEditors: React.Dispatch<React.SetStateAction<Map<string, BookingListDocument>>>;
      positions: ReadonlyMap<number, BookingListDocument>;
    }
  | undefined
>(undefined);

export function InlineBookingEditor({
  event: initialEvent,
  timezone,
  onClose,
}: {
  event: BookingListDocument & EditableBooking;
  timezone: string;
  onClose: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const [event, setEvent] = React.useState(initialEvent);
  const [formEvent, setFormEvent] = React.useState(initialEvent);
  const anchorEvent = React.useRef(initialEvent).current;
  const editorId = React.useId();
  const setEditors = React.useContext(BookingEditorsContext)?.setEditors;
  React.useEffect(() => {
    setEditors?.((current) => new Map(current).set(editorId, anchorEvent));
    return () =>
      setEditors?.((current) => {
        const next = new Map(current);
        next.delete(editorId);
        return next;
      });
  }, [editorId, anchorEvent, setEditors]);
  const configuration = useBookableItemConfiguration(event.target.globalId);
  const updateOptions = getUpdateBookingMutationOptions(queryClient);
  const mutation = useMutation({
    mutationFn: (submission: BookingFormSubmission) => {
      const patch: BookingsUpdate = {
        ...(submission.window.start !== event.start ? { start: submission.window.start } : {}),
        ...(submission.window.end !== event.end ? { end: submission.window.end } : {}),
        ...(submission.purpose !== event.purpose ? { purpose: submission.purpose } : {}),
      };
      return Object.keys(patch).length === 0 ? Promise.resolve(event) : updateBooking(event.id, event.version, patch);
    },
    onSuccess: async (updated, _variables, onMutateResult, mutationContext) => {
      if (updated) {
        await updateOptions.onSuccess?.(
          updated,
          { id: event.id, data: {}, headers: { "If-Match": `"${event.version}"` } },
          onMutateResult,
          mutationContext,
        );
      }
      await Promise.all([
        invalidateListBookings(queryClient, undefined, { cancelRefetch: false }),
        invalidateGetBooking(queryClient, event.id, undefined, { cancelRefetch: false }),
      ]);
      onClose();
    },
    onError: async (error) => {
      if (
        isBookingOutcomeUncertain(error) ||
        (error instanceof ApiV2ProblemError &&
          (error.status === 412 ||
            error.code === "errors.api.v2.booking.concurrentModification" ||
            error.code === "errors.api.v2.forbidden" ||
            error.code === "errors.api.v2.booking.state.transition"))
      ) {
        await Promise.all([invalidateListBookings(queryClient), invalidateGetBooking(queryClient, event.id)]);
      }
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await invalidateListBookingConfigurations(queryClient);
      }
    },
  });
  const mutationHasError = React.useRef(false);
  if (event !== initialEvent && !mutation.isPending) {
    setEvent(initialEvent);
    if (!isBookingOutcomeUncertain(mutation.error)) setFormEvent(initialEvent);
  }
  mutationHasError.current = mutation.isError;
  const resetMutation = mutation.reset;
  const clearMutationErrorOnChange = React.useCallback(() => {
    if (!mutationHasError.current) return;
    mutationHasError.current = false;
    resetMutation();
  }, [resetMutation]);

  if (configuration.isPending) {
    return (
      <div className="space-y-4 border-border border-t p-4" aria-busy="true">
        <p role="status" className="sr-only">
          {t("bookings.loadingConfiguration")}
        </p>
        <Skeleton aria-hidden="true" className="h-12 w-full" />
        <Skeleton aria-hidden="true" className="h-24 w-full" />
      </div>
    );
  }
  if (configuration.isError || !configuration.data) {
    return (
      <div className="space-y-3 border-border border-t p-4">
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" onClick={() => void configuration.refetch()}>
            {t("common:actions.retry")}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t("bookings.form.cancel")}
          </Button>
        </div>
      </div>
    );
  }
  return (
    <div className="flex max-h-[min(22rem,45vh)] flex-col border-border border-t">
      <BookingForm
        key={formEvent.version}
        mode="edit"
        density="compact"
        displayTimezone={timezone}
        booking={formEvent}
        configuration={configuration.data}
        pending={mutation.isPending}
        error={
          mutation.error
            ? t(
                isBookingOutcomeUncertain(mutation.error)
                  ? "bookings.errors.outcomeUncertain"
                  : bookingProblemKey(mutation.error),
              )
            : undefined
        }
        submissionBlocked={isBookingOverlapError(mutation.error)}
        onStateChange={clearMutationErrorOnChange}
        onCancel={() => {
          mutation.reset();
          onClose();
        }}
        onSubmit={(submission) => mutation.mutateAsync(submission)}
      />
    </div>
  );
}

export function BookingEventsCalendar({
  date,
  view,
  layout,
  timezone,
  availabilityStartMinute = 7 * 60,
  availabilityEndMinute = 19 * 60,
  events,
  resources,
  currentUserId,
  isLoading,
  isError,
  onRetry,
  onDateChange,
  onControlsReset,
  onViewChange,
  onLayoutChange,
  creationAction,
  resourceConfigurations,
  resourceTableProps,
  creationDisabled = false,
  onResourceRangeSelect,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  availabilityStartMinute?: number;
  availabilityEndMinute?: number;
  events: readonly BookingListDocument[];
  /** Enabled configurations to render as resource rows, including resources with no events. */
  resources?: readonly BookingCalendarResource[];
  currentUserId: number;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDateChange: (date: string) => void;
  onControlsReset: () => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
  /** Production creation action rendered without replacing the calendar controls. */
  creationAction?: React.ReactNode;
  resourceConfigurations?: readonly BookableItemOption[];
  resourceTableProps?: TableListProps<BookingConfiguration>;
  creationDisabled?: boolean;
  onResourceRangeSelect?: (
    resource: BookableItemOption,
    range: { startMinute: number; endMinute: number },
    trigger: HTMLElement,
  ) => void;
}) {
  const { t } = useTranslation("booking");
  const todayValue = todayInTimeZone(timezone);
  const [mineOnly, setMineOnly] = React.useState(false);
  const [editors, setEditors] = React.useState(() => new Map<string, BookingListDocument>());
  // Pin open cards by booking id so reconciliation can show new times without unmounting their drafts.
  const editorContext = React.useMemo(
    () => ({ setEditors, positions: new Map([...editors.values()].map((event) => [event.id, event])) }),
    [editors],
  );
  const displayedEvents = React.useMemo(() => {
    const byId = new Map(events.map((event) => [event.id, event]));
    for (const event of editors.values()) {
      if (!byId.has(event.id)) byId.set(event.id, event);
    }
    return [...byId.values()];
  }, [editors, events]);
  const mine = displayedEvents.filter((event) => {
    const requesterValue = event.requester?.value;
    const requesterId = typeof requesterValue === "number" ? requesterValue : requesterValue?.id;
    return requesterId === currentUserId;
  });
  const table = useTableList({
    config: bookingEventListConfig,
    dataSource: { type: "client", rows: mineOnly ? mine : displayedEvents },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
  });
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8">
      {isLoading && !isError && (
        <p role="status" className="sr-only">
          {t("calendar.loading")}
        </p>
      )}
      {isError && (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("calendar.unavailable")}</span>
          <Button type="button" variant="outline" onClick={onRetry}>
            {t("calendar.retry")}
          </Button>
        </div>
      )}
      {!isError && (
        <TableList
          {...table.tableProps}
          createAction={creationAction}
          filterButtons={{
            legend: t("calendar.quickFilters.legend"),
            controls: (
              <CalendarFilterControls
                date={date}
                view={view}
                layout={layout}
                timezone={timezone}
                today={todayValue}
                onDateChange={onDateChange}
                onViewChange={onViewChange}
                onLayoutChange={onLayoutChange}
              />
            ),
            hasChanges: date !== todayValue || view !== "day" || layout !== "resources",
            buttons: [
              {
                id: "mine",
                label: t("calendar.quickFilters.mine"),
                icon: <CalendarCheck2Icon aria-hidden="true" />,
                pressed: mineOnly,
                count: isLoading ? undefined : mine.length,
                onClick: () => setMineOnly((current) => !current),
              },
            ],
            onReset: () => {
              setMineOnly(false);
              onControlsReset();
            },
          }}
          renderRowsWhenEmpty
          renderRows={(filteredEvents) => (
            <BookingEditorsContext.Provider value={editorContext}>
              {layout === "time-grid" && (
                <CalendarTimeGrid
                  date={date}
                  view={view}
                  events={filteredEvents}
                  timezone={timezone}
                  today={todayValue}
                  availabilityStartMinute={availabilityStartMinute}
                  availabilityEndMinute={availabilityEndMinute}
                  isLoading={isLoading}
                />
              )}
              {layout === "resources" &&
                (resourceTableProps ? (
                  <TableList
                    {...resourceTableProps}
                    status={resourceTableProps.status === "loading" ? "idle" : resourceTableProps.status}
                    renderRowsWhenEmpty={resourceTableProps.status === "loading"}
                    hideHeader
                    // The resource fetch honours only the search term, so a filter panel here
                    // would build an expression nothing reads.
                    hideFilterPanel
                    variant="transparent"
                    renderRows={() =>
                      resourceTableProps.status === "loading" ? (
                        <ResourceScheduleSkeleton period={{ date, view }} />
                      ) : (
                        <CalendarResourceSchedule
                          date={date}
                          view={view}
                          events={filteredEvents}
                          resources={resources}
                          timezone={timezone}
                          today={todayValue}
                          availabilityStartMinute={availabilityStartMinute}
                          availabilityEndMinute={availabilityEndMinute}
                          resourceConfigurations={resourceConfigurations}
                          creationDisabled={creationDisabled}
                          isLoading={isLoading}
                          onResourceRangeSelect={onResourceRangeSelect}
                        />
                      )
                    }
                  />
                ) : (
                  <CalendarResourceSchedule
                    date={date}
                    view={view}
                    events={filteredEvents}
                    resources={resources}
                    timezone={timezone}
                    today={todayValue}
                    availabilityStartMinute={availabilityStartMinute}
                    availabilityEndMinute={availabilityEndMinute}
                    resourceConfigurations={resourceConfigurations}
                    creationDisabled={creationDisabled}
                    isLoading={isLoading}
                    onResourceRangeSelect={onResourceRangeSelect}
                  />
                ))}
              {layout === "agenda" && (
                <CalendarAgenda
                  date={date}
                  view={view}
                  events={filteredEvents}
                  timezone={timezone}
                  today={todayValue}
                  isLoading={isLoading}
                />
              )}
            </BookingEditorsContext.Provider>
          )}
        />
      )}
    </main>
  );
}

export type {
  BookingCalendarResource,
  CalendarLayout,
  CalendarView,
} from "@/modules/booking/pages/calendar/calendarLayoutUtils";
