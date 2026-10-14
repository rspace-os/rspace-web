// PROTOTYPE ONLY: in-memory personal notification actions on readable items.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy stays out of production catalogs. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarClockIcon, Clock3Icon } from "lucide-react";
import { useState } from "react";
import { AvailabilityBar, type AvailabilityInterval } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import { bookableItemFixtures } from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList, type TableListFilterButtons } from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/tableListState";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";

type QuickFilter = "available-now" | "free-later-today";
type Instrument = {
  id: string;
  name: string;
  location: string | null;
  locationId: string | null;
  timezone: string;
  quickFilter: QuickFilter;
};

const displayTimeZone = "Europe/Berlin";
const quickFilterByInstrument: Record<string, QuickFilter> = {
  IN123: "available-now",
  IN124: "free-later-today",
  IN125: "available-now",
  IN126: "free-later-today",
  IN127: "available-now",
};
const bookingWindows: Record<string, readonly { kind: AvailabilityInterval["kind"]; start: string; end: string }[]> = {
  IN123: [{ kind: "booking", start: "10:00", end: "11:30" }],
  IN124: [{ kind: "booking", start: "13:00", end: "14:30" }],
  IN125: [{ kind: "blockout", start: "15:00", end: "16:00" }],
  IN126: [{ kind: "booking", start: "09:00", end: "10:00" }],
  IN127: [],
};

const instruments: Instrument[] = bookableItemFixtures.map((item) => ({
  id: item.target.globalId,
  name: item.target.value.name,
  location: item.target.value.parentContainerName,
  locationId: item.target.value.parentContainerGlobalId,
  timezone: item.timezone,
  quickFilter: quickFilterByInstrument[item.target.globalId],
}));

function availabilityIntervals(globalId: string, date: string): AvailabilityInterval[] {
  return (bookingWindows[globalId] ?? []).map(({ kind, start, end }) => {
    const interval = displayInterval(date, displayTimeZone, start, end);
    return { kind, startsAt: new Date(interval.start), endsAt: new Date(interval.end) };
  });
}

const config = resolveCollectionConfig<Instrument>({
  slug: "prototype-bookable-notifications",
  idField: "id",
  useAsTitle: "name",
  labels: { singularKey: "booking:allBookableItems.singular", pluralKey: "booking:allBookableItems.plural" },
  defaultColumns: ["name"],
  listSearchableFields: ["name"],
  fields: [
    { name: "id", type: "text", labelKey: "booking:bookableItems.fields.id", list: false },
    {
      name: "name",
      type: "text",
      labelKey: "booking:bookableItems.fields.target",
      list: {
        renderCell: ({ row }: { row: Instrument }) => (
          <InventoryItem name={row.name} globalId={row.id} size="xs">
            <InventoryLocationLink name={row.location} globalId={row.locationId} />
          </InventoryItem>
        ),
      },
    },
  ],
});

function BulkNotificationsPrototype({
  initiallySelected = [],
  initiallySubscribed = ["IN123"],
}: {
  initiallySelected?: string[];
  initiallySubscribed?: string[];
}) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set(initiallySelected));
  const [subscriptions, setSubscriptions] = useState<ReadonlySet<string>>(new Set(initiallySubscribed));
  const [filters, setFilters] = useState<FilterState<Instrument>>({ search: "", expression: null });
  const today = todayInTimeZone(displayTimeZone);
  const [selectedDate, setSelectedDate] = useState(today);
  const [quickFilter, setQuickFilter] = useState<QuickFilter>();
  const [feedback, setFeedback] = useState("");
  const bounds = displayInterval(selectedDate, displayTimeZone, "08:00", "18:00");
  const rows = quickFilter ? instruments.filter((item) => item.quickFilter === quickFilter) : instruments;

  function changeDate(date: string) {
    setSelectedDate(date);
    setQuickFilter(undefined);
    setSelected(new Set());
  }

  function resetAvailability() {
    setSelectedDate(today);
    setQuickFilter(undefined);
    setFilters({ search: "", expression: null });
    setSelected(new Set());
  }

  const filterButtons: TableListFilterButtons = {
    legend: "Availability filters",
    controlsOnSeparateRow: true,
    controls: (
      <BookingDateControls
        date={selectedDate}
        today={today}
        timeZone={displayTimeZone}
        controlsLabel="Availability date controls"
        navigationLabel="Availability date navigation"
        previousLabel="Previous day"
        todayLabel="Today"
        nextLabel="Next day"
        jumpToDateLabel="Jump to date"
        onPrevious={() => changeDate(addCalendarDays(selectedDate, -1))}
        onNext={() => changeDate(addCalendarDays(selectedDate, 1))}
        onDateChange={changeDate}
      />
    ),
    hasChanges: selectedDate !== today || quickFilter !== undefined,
    buttons: [
      {
        id: "available-now",
        label: "Available now",
        icon: <Clock3Icon aria-hidden="true" />,
        pressed: quickFilter === "available-now",
        onClick: () => {
          setQuickFilter(quickFilter === "available-now" ? undefined : "available-now");
          setSelected(new Set());
        },
      },
      {
        id: "free-later-today",
        label: "Free later today",
        icon: <CalendarClockIcon aria-hidden="true" />,
        pressed: quickFilter === "free-later-today",
        onClick: () => {
          setQuickFilter(quickFilter === "free-later-today" ? undefined : "free-later-today");
          setSelected(new Set());
        },
      },
    ],
    onReset: resetAvailability,
  };

  function apply(subscribe: boolean) {
    const next = new Set(subscriptions);
    for (const id of selected) {
      if (subscribe) next.add(id);
      else next.delete(id);
    }
    setSubscriptions(next);
    setFeedback(
      (subscribe ? "Subscribed to " : "Unsubscribed from ") +
        selected.size +
        " item" +
        (selected.size === 1 ? "" : "s") +
        ". This change applies only to your account.",
    );
    setSelected(new Set());
  }

  return (
    <main className="space-y-5 p-4 sm:p-8">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="outline">Prototype · bulk notifications</Badge>
      </div>
      <TableList
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        queryString={false}
        clientSide
        reserveEmptyRows={false}
        variant="transparent"
        filterButtons={filterButtons}
        onReset={resetAvailability}
        presentations={{ table: "wide", cards: "narrow" }}
        features={{
          filtering: {
            value: filters,
            onChange: (value) => {
              setFilters(value);
              setSelected(new Set());
            },
          },
          sorting: false,
          pagination: false,
          columns: false,
        }}
        headerContent={
          <p className="text-sm text-muted-foreground">Select items to subscribe or unsubscribe yourself.</p>
        }
        uiColumns={[
          {
            id: "availability",
            label: "Availability",
            minWidth: 320,
            width: 520,
            card: { fullWidth: true },
            renderCell: (item) => (
              <AvailabilityBar
                intervals={availabilityIntervals(item.id, selectedDate)}
                periodStart={new Date(bounds.start)}
                periodEnd={new Date(bounds.end)}
                now={selectedDate === today ? new Date() : undefined}
                showBookingContextDetails={false}
                showCurrentAvailability
                showPeriodLabels
                timeZone={displayTimeZone}
                instrumentTimeZone={item.timezone}
                item={{
                  name: item.name,
                  globalId: item.id,
                  ...(item.location && item.locationId
                    ? { location: { name: item.location, globalId: item.locationId } }
                    : {}),
                }}
              />
            ),
          },
        ]}
        selection={{
          value: selected,
          onChange: setSelected,
          maximumCount: instruments.length,
          getRowLabel: (row) => row.name,
          renderActions: () => (
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" disabled={selected.size === 0} onClick={() => apply(true)}>
                Subscribe
              </Button>
              <Button size="sm" variant="outline" disabled={selected.size === 0} onClick={() => apply(false)}>
                Unsubscribe
              </Button>
            </div>
          ),
        }}
      />
      <p role="status" className="min-h-6 text-sm text-primary">
        {feedback}
      </p>
      <p className="text-sm text-muted-foreground">
        You subscribe to {subscriptions.size} of the {instruments.length} readable items in this example.
      </p>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Notifications bulk actions",
  component: BulkNotificationsPrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof BulkNotificationsPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;
export const AllReadableItems: Story = {};
export const OwnerSelection: Story = { args: { initiallySelected: ["IN123", "IN124"] } };
export const ViewerCanSubscribe: Story = {
  args: { initiallySelected: ["IN126"] },
};
export const BookerCanSubscribe: Story = {
  args: { initiallySelected: ["IN127"] },
};
export const ViewerCanUnsubscribe: Story = {
  args: { initiallySelected: ["IN126"], initiallySubscribed: ["IN123", "IN126"] },
};
