// PROTOTYPE ONLY. Throwaway Opus 5.5 concept for Plan 008 bulk notification subscriptions; not production code.
// Prototype question: on All bookable items, can one selection mixing owned and readable non-owned instruments
// subscribe or unsubscribe the current user to any visible instrument, without a per-row
// notification column or toggle?
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarClockIcon, Clock3Icon, PackageCheckIcon } from "lucide-react";
import { useState } from "react";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { type AvailabilityInterval, classifyCurrentDayAvailability } from "@/modules/booking/domain/availability";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { BookingConfiguration } from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import {
  bookableItemFixtures,
  bookerBookingAccess,
  sampleBookingEvents,
} from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList, type TableListFilterButtons } from "@/modules/common/table-list/TableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";

// ponytail: fixture configs re-typed; `as const` roleSources is readonly and must be replaced.
const owned = (index: 0 | 1): BookingConfiguration => ({
  ...bookableItemFixtures[index],
  roleSources: [],
  capabilities: { ...bookableItemFixtures[index].capabilities, canManageNotificationSubscription: true },
});
const reader = (index: 2 | 3 | 4, effectiveRole: "BOOKER" | "VIEWER"): BookingConfiguration => ({
  ...bookableItemFixtures[index],
  ...bookerBookingAccess,
  effectiveRole,
  roleSources: [],
  capabilities: {
    ...bookerBookingAccess.capabilities,
    canCreateBooking: effectiveRole === "BOOKER",
    canManageOwnBookings: effectiveRole === "BOOKER",
    canManageNotificationSubscription: true,
  },
});
const rows: readonly BookingConfiguration[] = [
  owned(0),
  owned(1),
  reader(2, "VIEWER"),
  reader(3, "VIEWER"),
  reader(4, "BOOKER"),
];
const today = "2026-08-17";
const displayTimeZone = "UTC";
const availabilityWindow = { start: "08:00", end: "18:00" } as const;
const availabilityBounds = displayInterval(today, displayTimeZone, availabilityWindow.start, availabilityWindow.end);
const availabilityNow = new Date("2026-08-17T08:30:00Z");

const availabilityIntervals = new Map<number, readonly AvailabilityInterval[]>(
  rows.map((row) => [
    row.id,
    sampleBookingEvents
      .filter((event) => event.target.globalId === row.target?.globalId)
      .map((event) => ({ kind: "booking", startsAt: new Date(event.start), endsAt: new Date(event.end) })),
  ]),
);
const availabilityCategories = new Map(
  rows.map((row) => [
    row.id,
    classifyCurrentDayAvailability(
      availabilityIntervals.get(row.id) ?? [],
      new Date(availabilityBounds.start),
      new Date(availabilityBounds.end),
      availabilityNow,
    ),
  ]),
);
// Saved rows for the signed-in user only, keyed by configuration id.
const initialSubscriptions: Record<number, boolean> = { 7: true };

const config = resolveCollectionConfig({
  slug: "opus-bulk-notifications",
  idField: "id",
  useAsTitle: "target",
  labels: {
    singularKey: "booking:allBookableItems.singular",
    pluralKey: "booking:allBookableItems.plural",
  },
  defaultColumns: ["target"],
  relationshipSources: bookingRelationshipSources,
  fields: [
    { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
      hasMany: false,
      labelKey: "booking:bookableItems.fields.target",
      capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
      list: {
        width: 280,
        minWidth: 240,
        renderCell: ({ row }: { row: BookingConfiguration }) =>
          row.target ? (
            <InventoryItem
              name={row.target.value.name}
              globalId={row.target.globalId}
              href={`/globalId/${row.target.globalId}`}
              idLinkLabel={`Open ${row.target.globalId}`}
              size="xs"
            >
              <InventoryLocationLink
                name={row.target.value.parentContainerName}
                globalId={row.target.value.parentContainerGlobalId}
              />
            </InventoryItem>
          ) : (
            <UnknownItem size="xs" />
          ),
      },
    },
  ],
} as const satisfies CollectionConfig<BookingConfiguration>);

type Feedback = { enabled: boolean; count: number };
type AvailabilityQuickFilter = "available-now" | "free-later-today";

function BulkNotificationsPrototype({ initialSelected }: { initialSelected: readonly string[] }) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set(initialSelected));
  const [subscriptions, setSubscriptions] = useState(initialSubscriptions);
  const [feedback, setFeedback] = useState<Feedback>();
  const [selectedDate, setSelectedDate] = useState(today);
  const [mineOnly, setMineOnly] = useState(false);
  const [availabilityFilter, setAvailabilityFilter] = useState<AvailabilityQuickFilter>();
  const subscribedNames = rows
    .filter((row) => subscriptions[row.id])
    .map((row) => row.target?.value.name)
    .join(", ");
  const visibleRows = rows.filter(
    (row) =>
      (!mineOnly || row.effectiveRole === "OWNER") &&
      (availabilityFilter === undefined || availabilityCategories.get(row.id) === availabilityFilter),
  );
  const selectedAvailabilityBounds = displayInterval(
    selectedDate,
    displayTimeZone,
    availabilityWindow.start,
    availabilityWindow.end,
  );
  const setDate = (nextDate: string) => {
    setSelected(new Set());
    setAvailabilityFilter(undefined);
    setSelectedDate(nextDate);
  };
  const resetView = () => {
    setSelected(new Set());
    setMineOnly(false);
    setAvailabilityFilter(undefined);
    setSelectedDate(today);
  };
  const availabilityFilters: TableListFilterButtons = {
    legend: "Availability quick filters",
    controlsOnSeparateRow: true,
    controls: (
      <BookingDateControls
        date={selectedDate}
        today={today}
        timeZone={displayTimeZone}
        controlsLabel="Date controls"
        navigationLabel="Date navigation"
        previousLabel="Previous day"
        todayLabel="Today"
        nextLabel="Next day"
        jumpToDateLabel="Jump to date"
        onPrevious={() => setDate(addCalendarDays(selectedDate, -1))}
        onNext={() => setDate(addCalendarDays(selectedDate, 1))}
        onDateChange={setDate}
      />
    ),
    hasChanges: selectedDate !== today || mineOnly || availabilityFilter !== undefined,
    buttons: [
      {
        id: "mine",
        label: "My items",
        icon: <PackageCheckIcon aria-hidden="true" />,
        count: rows.filter((row) => row.effectiveRole === "OWNER").length,
        pressed: mineOnly,
        onClick: () => {
          setSelected(new Set());
          setMineOnly((current) => !current);
        },
      },
      {
        id: "available-now",
        label: "Available now",
        icon: <Clock3Icon aria-hidden="true" />,
        count: rows.filter((row) => availabilityCategories.get(row.id) === "available-now").length,
        pressed: availabilityFilter === "available-now",
        onClick: () => {
          setSelected(new Set());
          setAvailabilityFilter((current) => (current === "available-now" ? undefined : "available-now"));
        },
      },
      {
        id: "free-later-today",
        label: "Free later today",
        icon: <CalendarClockIcon aria-hidden="true" />,
        count: rows.filter((row) => availabilityCategories.get(row.id) === "free-later-today").length,
        pressed: availabilityFilter === "free-later-today",
        onClick: () => {
          setSelected(new Set());
          setAvailabilityFilter((current) => (current === "free-later-today" ? undefined : "free-later-today"));
        },
      },
    ],
    onReset: resetView,
  };

  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <div className="min-h-screen bg-background text-foreground">
        <nav aria-label="Prototype views" className="flex items-center gap-2 border-b px-4 py-2 sm:px-8">
          <Badge variant="outline">Prototype</Badge>
          <span className="text-xs text-muted-foreground">All bookable items, bulk notifications</span>
        </nav>
        <main className="space-y-5 p-4 sm:p-8">
          {feedback ? (
            <p role="status" className="text-sm text-primary">
              {feedback.enabled ? "Subscribed to" : "Unsubscribed from"} {feedback.count}{" "}
              {feedback.count === 1 ? "instrument" : "instruments"}.
            </p>
          ) : null}
          <TableList
            config={config}
            rows={visibleRows}
            getRowId={(row) => String(row.id)}
            clientSide
            queryString={false}
            reserveEmptyRows={false}
            headingClassName="text-2xl font-semibold"
            presentations={{ table: "wide", cards: "narrow" }}
            features={{ filtering: false, sorting: false, pagination: false, columns: false }}
            filterButtons={availabilityFilters}
            headerContent={
              <p className="text-sm text-muted-foreground">Select instruments to subscribe or unsubscribe yourself.</p>
            }
            uiColumns={[
              {
                id: "availability",
                label: "Availability",
                minWidth: 320,
                width: 520,
                card: { fullWidth: true },
                renderCell: (row) => {
                  if (!row.target || !row.timezone) return "Availability unavailable";
                  const item = {
                    name: row.target.value.name,
                    globalId: row.target.globalId,
                    ...(row.target.value.parentContainerName != null && row.target.value.parentContainerGlobalId != null
                      ? {
                          location: {
                            name: row.target.value.parentContainerName,
                            globalId: row.target.value.parentContainerGlobalId,
                          },
                        }
                      : {}),
                  };
                  return (
                    <AvailabilityBar
                      intervals={availabilityIntervals.get(row.id) ?? []}
                      periodStart={new Date(selectedAvailabilityBounds.start)}
                      periodEnd={new Date(selectedAvailabilityBounds.end)}
                      now={selectedDate === today ? availabilityNow : undefined}
                      showBookingContextDetails={false}
                      showCurrentAvailability
                      showPeriodLabels
                      timeZone={displayTimeZone}
                      instrumentTimeZone={row.timezone}
                      item={item}
                    />
                  );
                },
              },
            ]}
            selection={{
              value: selected,
              onChange: setSelected,
              maximumCount: 100,
              getRowLabel: (row) => row.target?.value.name ?? "Unknown item",
              renderActions: (selection) => {
                const selectedIds = [...selection.selectedRowIds];
                const run = (enabled: boolean) => {
                  setSubscriptions((current) => ({
                    ...current,
                    ...Object.fromEntries(selectedIds.map((id) => [id, enabled])),
                  }));
                  setFeedback({ enabled, count: selectedIds.length });
                  selection.clearSelection();
                };
                return (
                  <div className="flex flex-wrap items-center gap-2">
                    <Button type="button" size="sm" disabled={selectedIds.length === 0} onClick={() => run(true)}>
                      Subscribe
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={selectedIds.length === 0}
                      onClick={() => run(false)}
                    >
                      Unsubscribe
                    </Button>
                  </div>
                );
              },
            }}
          />
          <aside className="rounded-sm border border-dashed p-3 text-xs text-muted-foreground">
            Prototype subscriptions: {subscribedNames || "no instruments"}.
          </aside>
        </main>
      </div>
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Notifications Bulk (Opus 5.5)",
  component: BulkNotificationsPrototype,
  parameters: { layout: "fullscreen" },
  args: { initialSelected: ["7", "8", "10", "11"] },
} satisfies Meta<typeof BulkNotificationsPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const MixedOwnershipSelection: Story = {};
export const AllItemsSelected: Story = { args: { initialSelected: ["7", "8", "9", "10", "11"] } };
export const NothingSelected: Story = { args: { initialSelected: [] } };
