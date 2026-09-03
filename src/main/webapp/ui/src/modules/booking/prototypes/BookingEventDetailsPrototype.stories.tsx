// PROTOTYPE ONLY (booking-event-details-page plan, .claude/booking-event-details-page-plan.md).
// Proof-of-concept for the dedicated booking-event details page, built to mirror the production
// Bookable Item Details Page (../pages/bookable-items/BookableItemPage.tsx) and its story harness:
//   - the same harness shape as BookableItemPage.story.tsx: a QueryClient seeded with the OAuth
//     token and booking display preferences, a real TanStack route tree mounted under /booking, and
//     a RouterProvider, so the page reads its id from route params and its timezone from the
//     production useBookingDisplayPreferences hook;
//   - the same page composition: <main className="mx-auto max-w-5xl …">, a SpotlightHeader built
//     from InventoryItem (nameAs="h1", idPlacement="title") with badges and actions on the right, a
//     details Card whose CardAction swaps Edit for Save/Cancel editing, a definition list using the
//     shared responsive inline-field classes, an "About this booking" metadata aside adopted from
//     BookingEventDetailsLayoutPrototype, an Empty error state with Retry, the production
//     DirtyNavigationGuard, and an sr-only aria-live save announcement.
// Two deliberate departures from BookableItemPage, both required by the plan:
//   1. Edit mode is a nested /edit CHILD ROUTE, not the bookable item's ?edit=true search param
//      (the plan puts the search-param mechanism out of scope). Because mode switches change the
//      pathname, they must pass ignoreBlocker so DirtyNavigationGuard fires on external navigation
//      only; the bookable item never needs this, as its switches only change search.
//   2. Mode switches use navigate() rather than the plan's <Link>. Storybook's tanstack framework
//      aliases @tanstack/react-router to a mock whose Link and Navigate are inert stubs, so Link
//      behaviour is not observable here and belongs in a Browser Mode test. Everything else
//      (createRouter, RouterProvider, useParams, useNavigate, useBlocker) is the real module.
// The booking itself is served from an in-memory fixture, not MSW, and the instrumentation panel
// counts booking GETs, PATCHes, and parent-shell mounts so the plan's "one mounted parent, one
// booking query across View -> Edit -> View" claim is observable. Times are UTC instants rendered
// in the display timezone, which each story seeds as a CUSTOM preference so the timezone subtitle
// is deterministic rather than dependent on the host machine's zone.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
  useNavigate,
  useParams,
  useRouterState,
} from "@tanstack/react-router";
import { ArrowLeftIcon, CalendarArrowDownIcon, PencilIcon } from "lucide-react";
import { NuqsAdapter } from "nuqs/adapters/react";
import * as React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import {
  bookingDisplayPreferencesQueryKey,
  useBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Textarea } from "@/modules/common/ui/textarea";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { inheritedBrowserBookingPreferences } from "../pages/preferences/bookingPreferencesFixtures";

// --- Fixtures -------------------------------------------------------------------------------------

type BookingEventDocument = {
  id: number;
  kind: "BOOKING" | "MAINTENANCE";
  state: "CONFIRMED" | "CANCELLED";
  /** Independent of `state`: the ordinary cancellation command happens to set both. */
  deleted: boolean;
  canEdit: boolean;
  canCancel: boolean;
  canViewConfiguration: boolean;
  archivedConfiguration: boolean;
  target: {
    name: string;
    globalId: string;
    parentContainerName: string | null;
    parentContainerGlobalId: string | null;
  };
  /** UTC instants, as REST API v2 returns them. */
  start: string;
  end: string;
  /** The configuration's CURRENT timezone, not a booking-time snapshot. */
  timezone: string;
  bookedBy: string | null;
  createdBy: string | null;
  /** Purpose for a booking, notes for maintenance. */
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

const base: Omit<BookingEventDocument, "id"> = {
  kind: "BOOKING",
  state: "CONFIRMED",
  deleted: false,
  canEdit: true,
  canCancel: true,
  canViewConfiguration: true,
  archivedConfiguration: false,
  target: {
    name: "Confocal microscope",
    globalId: "IN123",
    parentContainerName: "Imaging suite, bench 2",
    parentContainerGlobalId: "IC55",
  },
  start: "2026-09-12T08:00:00Z",
  end: "2026-09-12T10:00:00Z",
  timezone: "Europe/Berlin",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: null,
  description: "Laser alignment ahead of the quarterly imaging run.\nBring the calibration slides.",
  createdAt: "2026-08-30T07:14:00Z",
  updatedAt: "2026-08-31T14:40:00Z",
};

const FIXTURES = {
  confirmed: { ...base, id: 42 },
  // A requester who lost their Booking role keeps read access to their own row and nothing else.
  "role-lost": { ...base, id: 43, canEdit: false, canCancel: false, canViewConfiguration: false },
  maintenance: {
    ...base,
    id: 44,
    kind: "MAINTENANCE" as const,
    bookedBy: null,
    createdBy: "Grace Hopper (grace)",
    description: "Replace the laser filter set and rerun the alignment checks.",
  },
  cancelled: { ...base, id: 45, state: "CANCELLED" as const, deleted: true, canEdit: false, canCancel: false },
  "confirmed-deleted": { ...base, id: 46, deleted: true, canEdit: false, canCancel: false },
  // Archived rejects editing but preserves cancellation for an eligible future event.
  archived: { ...base, id: 47, archivedConfiguration: true, canEdit: false },
  "cancelled-non-deleted": { ...base, id: 48, state: "CANCELLED" as const, canEdit: false, canCancel: false },
} satisfies Record<string, BookingEventDocument>;

type ScenarioKey = keyof typeof FIXTURES | "not-found" | "server-error";

// --- Simulated server ------------------------------------------------------------------------------

/** Stands in for REST API v2. Mutated in place so an invalidated query refetches the new state. */
type SimulatedServer = {
  scenario: ScenarioKey;
  document: BookingEventDocument | null;
  gets: number;
  patches: number;
  parentMounts: number;
  /** Cleared by Retry so the error state is recoverable. */
  failing: boolean;
};

const ServerContext = React.createContext<SimulatedServer | null>(null);
const useServer = () => {
  const server = React.useContext(ServerContext);
  if (server === null) throw new Error("missing prototype server");
  return server;
};

const bookingQueryKey = (id: number) => ["api-v2", "bookings", id] as const;

function useBookingQuery(id: number) {
  const server = useServer();
  return useQuery({
    queryKey: bookingQueryKey(id),
    queryFn: () => {
      server.gets += 1;
      if (server.failing) throw new Error("simulated server error");
      return server.document;
    },
    retry: false,
  });
}

// --- Action gates, from the plan's test matrix -----------------------------------------------------

const showEdit = (doc: BookingEventDocument) => doc.canEdit && doc.state === "CONFIRMED" && !doc.deleted;
const showCancel = (doc: BookingEventDocument) => doc.canCancel && doc.state === "CONFIRMED";
const showIcs = (doc: BookingEventDocument) => doc.canViewConfiguration && doc.state === "CONFIRMED" && !doc.deleted;

/** An instant as a `datetime-local` input value in the given zone. */
function toLocalInput(instant: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(instant));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((candidate) => candidate.type === type)?.value ?? "00";
  return `${part("year")}-${part("month")}-${part("day")}T${part("hour")}:${part("minute")}`;
}

// ponytail: the inverse, by reading the zone's offset at that moment. Ambiguous wall clocks inside a
// DST fold resolve to one of the two instants; a production edit path should use the real form and
// the server's own timezone handling rather than this.
function fromLocalInput(local: string, timeZone: string): string {
  const asIfUtc = new Date(`${local}:00Z`).getTime();
  const offset = asIfUtc - new Date(`${toLocalInput(new Date(asIfUtc).toISOString(), timeZone)}:00Z`).getTime();
  return new Date(asIfUtc + offset).toISOString();
}

/**
 * The context-aware return destination. Real source context lives in typed history state (plan
 * step 4); this prototype only models the capability gate that decides between the two, because
 * the history object is routing context and never authorization. A reader who cannot view the
 * configuration must not be offered a Calendar return even if the context asks for one.
 */
const returnDestination = (doc: BookingEventDocument) =>
  doc.canViewConfiguration
    ? `Return to calendar (12 Sep 2026, ${doc.target.globalId})`
    : "Return to My Bookings (upcoming)";

// --- Page chrome, mirroring BookableItemPage --------------------------------------------------------

/** The bookable item's SpotlightHeader, rebuilt around an event rather than a configuration. */
function SpotlightHeader({ doc, action }: { doc: BookingEventDocument; action?: React.ReactNode }) {
  return (
    <section className="flex flex-wrap items-center gap-4">
      <InventoryItem
        name={doc.target.name}
        nameAs="h1"
        globalId={doc.target.globalId}
        idPlacement="title"
        className="min-w-full flex-1 p-0 sm:min-w-0"
        // Only a configuration reader may navigate to the bookable item.
        href={doc.canViewConfiguration ? `/booking/bookable-items/${doc.target.globalId}` : undefined}
        idLinkLabel={doc.canViewConfiguration ? `View bookable item ${doc.target.globalId}` : undefined}
      >
        {/* Plain text: a Booking role never implies Inventory access, so no /globalId/ link. */}
        {doc.target.parentContainerName === null ? null : (
          <span>{`${doc.target.parentContainerName} (${doc.target.parentContainerGlobalId})`}</span>
        )}
      </InventoryItem>
      <div
        data-slot="booking-event-header-actions"
        className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0 [&_[data-slot=badge]]:h-[30px] [&_button]:h-[30px] [&_button]:min-h-[30px]"
      >
        <Badge variant="outline">{doc.kind === "MAINTENANCE" ? "Maintenance" : "Booking"}</Badge>
        <Badge variant={doc.state === "CANCELLED" ? "destructive" : "default"}>
          {doc.state === "CANCELLED" ? "Cancelled" : "Confirmed"}
        </Badge>
        {/* Deletion is shown separately; the state badge is never a proxy for it. */}
        {doc.deleted ? <Badge variant="secondary">Removed from booking lists</Badge> : null}
        {doc.archivedConfiguration ? <Badge variant="secondary">Configuration archived</Badge> : null}
        {action}
      </div>
    </section>
  );
}

/** The bookable item's RulesReadOut, rebuilt over event facts. */
function EventReadOut({ doc, displayTimeZone }: { doc: BookingEventDocument; displayTimeZone: string }) {
  const facts: Array<[string, React.ReactNode]> = [
    [
      "When",
      <>
        <time dateTime={doc.start}>{formatDateTime(doc.start, displayTimeZone)}</time>
        {" – "}
        <time dateTime={doc.end}>{formatDateTime(doc.end, displayTimeZone)}</time>
      </>,
    ],
    ...(doc.bookedBy === null
      ? []
      : ([["Booked by", <UserBadge key="booked-by" name={doc.bookedBy} />]] as Array<[string, React.ReactNode]>)),
    ...(doc.createdBy === null
      ? []
      : ([["Created by", <UserBadge key="created-by" name={doc.createdBy} />]] as Array<[string, React.ReactNode]>)),
    [
      doc.kind === "MAINTENANCE" ? "Notes" : "Purpose",
      doc.description === null ? (
        <span className="text-muted-foreground">None provided</span>
      ) : (
        <span className="whitespace-pre-line">{doc.description}</span>
      ),
    ],
  ];

  return (
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
  );
}

function formatDateTime(value: string, timeZone: string) {
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone }).format(
    new Date(value),
  );
}

function BookingMetadataAside({ doc, displayTimeZone }: { doc: BookingEventDocument; displayTimeZone: string }) {
  const heading = doc.kind === "MAINTENANCE" ? "About this maintenance event" : "About this booking";

  return (
    <aside aria-labelledby="booking-metadata-heading" className="min-w-0">
      <Card size="sm">
        <CardHeader>
          <CardTitle id="booking-metadata-heading">{heading}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl data-slot="timestamps" className="space-y-3 text-sm">
            <div>
              <dt className="text-muted-foreground">Item</dt>
              <dd className="min-w-0">
                <InventoryItem
                  name={doc.target.name}
                  globalId={doc.target.globalId}
                  size="xs"
                  href={doc.canViewConfiguration ? `/booking/bookable-items/${doc.target.globalId}` : undefined}
                  idLinkLabel={doc.canViewConfiguration ? `View bookable item ${doc.target.globalId}` : undefined}
                >
                  {doc.target.parentContainerName === null ? null : (
                    <span>{`${doc.target.parentContainerName} (${doc.target.parentContainerGlobalId})`}</span>
                  )}
                </InventoryItem>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Times shown in</dt>
              <dd>{displayTimeZone}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Created</dt>
              <dd>
                <time dateTime={doc.createdAt}>{formatDateTime(doc.createdAt, displayTimeZone)}</time>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Last updated</dt>
              <dd>
                <time dateTime={doc.updatedAt}>{formatDateTime(doc.updatedAt, displayTimeZone)}</time>
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </aside>
  );
}

// --- Loaded-event context (plan step 4) --------------------------------------------------------------

type LoadedEvent = {
  doc: BookingEventDocument;
  displayTimeZone: string;
  formId: string;
  editButtonRef: React.RefObject<HTMLButtonElement | null>;
  announce: (message: string) => void;
  setDirty: (dirty: boolean) => void;
  refreshBooking: () => Promise<void>;
};

const LoadedEventContext = React.createContext<LoadedEvent | null>(null);
const useLoadedEvent = () => {
  const value = React.useContext(LoadedEventContext);
  if (value === null) throw new Error("child rendered outside the event page");
  return value;
};

/** Mode switches are internal, so they bypass the dirty blocker the way plan step 5 requires. */
const MODE_NAVIGATION = { replace: true, resetScroll: false, ignoreBlocker: true } as const;

// ponytail: `to` is type-checked against the app's REGISTERED route tree, which has no /edit child
// yet, so the prototype's own tree cannot satisfy it. One cast here keeps every other call honestly
// typed; it disappears once plan step 3 registers the real subtree and `to` + `params` type-check.
function navigateToMode(navigate: ReturnType<typeof useNavigate>, id: number, mode: "view" | "edit") {
  void navigate({
    to: `/booking/calendar/bookings/${id}${mode === "edit" ? "/edit" : ""}`,
    ...MODE_NAVIGATION,
  } as never);
}

// --- The parent event route -------------------------------------------------------------------------

function BookingEventPage() {
  const { id } = useParams({ strict: false });
  const server = useServer();
  const queryClient = useQueryClient();
  const preferences = useBookingDisplayPreferences();
  const parsedId = Number(id);
  const booking = useBookingQuery(parsedId);
  const [announcement, setAnnouncement] = React.useState("");
  const [dirty, setDirty] = React.useState(false);
  const [cancelOpen, setCancelOpen] = React.useState(false);
  const editButtonRef = React.useRef<HTMLButtonElement>(null);
  const formId = `booking-event-${React.useId()}`;

  React.useEffect(() => {
    server.parentMounts += 1;
  }, [server]);

  if (booking.isPending) {
    return (
      <main className="p-4 sm:p-8">
        <p role="status">Loading booking…</p>
      </main>
    );
  }

  const doc = booking.data ?? null;
  // Missing and inaccessible ids are indistinguishable; only a transport failure offers Retry.
  if (booking.isError || doc === null) {
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{booking.isError ? "Something went wrong" : "Booking unavailable"}</EmptyTitle>
            <EmptyDescription>
              {booking.isError
                ? "The booking could not be loaded."
                : "This booking does not exist or you do not have access to it."}
            </EmptyDescription>
          </EmptyHeader>
          {/* No calendar or bookable-item return here: there is no capability document to trust. */}
          <div className="flex gap-2">
            <Button type="button" variant="outline">
              Return to My Bookings
            </Button>
            {booking.isError ? (
              <Button
                type="button"
                onClick={() => {
                  server.failing = false;
                  void booking.refetch();
                }}
              >
                Retry
              </Button>
            ) : null}
          </div>
        </Empty>
      </main>
    );
  }

  const refreshBooking = async () => {
    await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
  };

  const cancelEvent = () => {
    // Cancellation sets the state AND soft-deletes; the two stay independent facts on read.
    server.document = { ...doc, state: "CANCELLED", deleted: true, canEdit: false, canCancel: false };
    setCancelOpen(false);
    setAnnouncement(doc.kind === "MAINTENANCE" ? "Maintenance event cancelled." : "Booking cancelled.");
    void refreshBooking();
  };

  const cancelLabel = doc.kind === "MAINTENANCE" ? "Cancel maintenance event" : "Cancel booking";

  return (
    <main className="@container mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <DirtyNavigationGuard dirty={dirty} />
      <PrototypeInstrumentation />

      {/*
        ponytail: an announcement instead of a real navigation. The prototype's route tree contains
        only the event subtree, so a Calendar or My Bookings target would resolve to notFound. The
        decision under test is which destination the capability gate offers, not the hop itself.
      */}
      <Button type="button" variant="ghost" size="sm" onClick={() => setAnnouncement(`${returnDestination(doc)}.`)}>
        <ArrowLeftIcon aria-hidden="true" />
        {returnDestination(doc)}
      </Button>

      <SpotlightHeader
        doc={doc}
        action={
          <>
            {showIcs(doc) ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => setAnnouncement("Downloaded confocal-microscope-IN123-2026-09-12.ics.")}
              >
                <CalendarArrowDownIcon aria-hidden="true" />
                .ics file
              </Button>
            ) : null}
            {showCancel(doc) ? (
              <Button type="button" variant="outline" onClick={() => setCancelOpen(true)}>
                {cancelLabel}
              </Button>
            ) : null}
          </>
        }
      />

      <LoadedEventContext.Provider
        value={{
          doc,
          displayTimeZone: preferences.timeZone,
          formId,
          editButtonRef,
          announce: setAnnouncement,
          setDirty,
          refreshBooking,
        }}
      >
        <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_16rem]">
          <Outlet />
          <BookingMetadataAside doc={doc} displayTimeZone={preferences.timeZone} />
        </div>
      </LoadedEventContext.Provider>

      <AlertDialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{cancelLabel}?</AlertDialogTitle>
            <AlertDialogDescription>
              {doc.kind === "MAINTENANCE"
                ? "The maintenance window is released and the slot becomes bookable again. The cancelled event stays readable from its direct link."
                : "The reserved time is released for others to book. The cancelled booking stays readable from its direct link but leaves booking lists."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep it</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={cancelEvent}>
              {cancelLabel}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </main>
  );
}

// --- The index child: view mode -----------------------------------------------------------------------

function BookingDetailsView() {
  const { doc, displayTimeZone, editButtonRef } = useLoadedEvent();
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{doc.kind === "MAINTENANCE" ? "Maintenance details" : "Booking details"}</CardTitle>
        {showEdit(doc) ? (
          <CardAction className="flex gap-3">
            <Button
              ref={editButtonRef}
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => navigateToMode(navigate, doc.id, "edit")}
            >
              <PencilIcon aria-hidden="true" />
              Edit
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        <EventReadOut doc={doc} displayTimeZone={displayTimeZone} />
      </CardContent>
    </Card>
  );
}

// --- The edit child: inline edit mode -------------------------------------------------------------------

function BookingInlineEditForm() {
  const { doc, displayTimeZone, formId, editButtonRef, announce, setDirty, refreshBooking } = useLoadedEvent();
  const server = useServer();
  const navigate = useNavigate();
  // The inputs hold wall clock in the display zone; the document keeps instants.
  const [draft, setDraft] = React.useState({
    start: toLocalInput(doc.start, displayTimeZone),
    end: toLocalInput(doc.end, displayTimeZone),
    description: doc.description ?? "",
  });
  const [saving, setSaving] = React.useState(false);
  const editable = showEdit(doc);

  const dirty =
    draft.start !== toLocalInput(doc.start, displayTimeZone) ||
    draft.end !== toLocalInput(doc.end, displayTimeZone) ||
    draft.description !== (doc.description ?? "");
  React.useEffect(() => setDirty(dirty), [dirty, setDirty]);
  React.useEffect(() => () => setDirty(false), [setDirty]);

  const toViewMode = React.useCallback(() => {
    navigateToMode(navigate, doc.id, "view");
    // The one-shot focus handoff back to the Edit action the view child owns.
    requestAnimationFrame(() => editButtonRef.current?.focus());
  }, [doc.id, editButtonRef, navigate]);

  // A readable but non-editable event normalizes to view mode rather than exposing a form.
  React.useEffect(() => {
    if (!editable) toViewMode();
  }, [editable, toViewMode]);
  if (!editable) return null;

  const save = async () => {
    setSaving(true);
    if (dirty) {
      server.patches += 1;
      server.document = {
        ...doc,
        start: fromLocalInput(draft.start, displayTimeZone),
        end: fromLocalInput(draft.end, displayTimeZone),
        description: draft.description,
        updatedAt: "2026-09-01T10:00:00Z",
      };
      // One invalidation; the mounted exact query refetches as part of it.
      await refreshBooking();
    }
    setSaving(false);
    setDirty(false);
    announce("Changes saved.");
    toViewMode();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{doc.kind === "MAINTENANCE" ? "Edit maintenance event" : "Edit booking"}</CardTitle>
        <CardAction className="flex gap-3">
          <Button type="submit" size="sm" form={formId} disabled={saving} aria-busy={saving}>
            Save changes
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            disabled={saving}
            onClick={() => {
              setDirty(false);
              toViewMode();
            }}
          >
            Cancel editing
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <form
          id={formId}
          className="min-w-0 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
            <div className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
              <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                <label className="font-medium" htmlFor={`${formId}-start`}>
                  Start
                </label>
                <Input
                  id={`${formId}-start`}
                  type="datetime-local"
                  disabled={saving}
                  value={draft.start}
                  onChange={(event) => setDraft((current) => ({ ...current, start: event.target.value }))}
                />
              </div>
              <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                <label className="font-medium" htmlFor={`${formId}-end`}>
                  End
                </label>
                <Input
                  id={`${formId}-end`}
                  type="datetime-local"
                  disabled={saving}
                  value={draft.end}
                  onChange={(event) => setDraft((current) => ({ ...current, end: event.target.value }))}
                />
              </div>
              <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                <label className="font-medium" htmlFor={`${formId}-description`}>
                  {doc.kind === "MAINTENANCE" ? "Notes" : "Purpose"}
                </label>
                <Textarea
                  id={`${formId}-description`}
                  disabled={saving}
                  value={draft.description}
                  onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
                />
              </div>
            </div>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// --- Prototype instrumentation (not part of the design) ------------------------------------------------

function PrototypeInstrumentation() {
  const server = useServer();
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  return (
    <div className="rounded-md border border-dashed p-2 font-mono text-xs text-muted-foreground">
      <p>Prototype instrumentation, not part of the design</p>
      <p>URL: {pathname}</p>
      <p>
        Booking GETs: {server.gets} · PATCHes: {server.patches} · shell mounts: {server.parentMounts}
      </p>
    </div>
  );
}

// --- Harness, mirroring BookableItemPage.story.tsx --------------------------------------------------------

/** The plan's step-3 factory: one parent event route with index and edit children. */
function createBookingEventRouteTree() {
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => rootRoute, path: "/booking", component: Outlet });
  const eventRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/calendar/bookings/$id",
    component: BookingEventPage,
  });
  const indexRoute = createRoute({ getParentRoute: () => eventRoute, path: "/", component: BookingDetailsView });
  const editRoute = createRoute({ getParentRoute: () => eventRoute, path: "edit", component: BookingInlineEditForm });
  return rootRoute.addChildren([bookingRoute.addChildren([eventRoute.addChildren([indexRoute, editRoute])])]);
}

function PrototypePage({
  scenario,
  mode = "view",
  displayTimezone = "Europe/London",
}: {
  scenario: ScenarioKey;
  mode?: "view" | "edit";
  /** Seeded as a CUSTOM preference so the subtitle condition never depends on the host machine. */
  displayTimezone?: string;
}) {
  const [{ queryClient, router, server }] = React.useState(() => {
    const server: SimulatedServer = {
      scenario,
      // A transient failure still has a row behind it, so Retry can succeed into the real document.
      document: scenario === "not-found" ? null : scenario === "server-error" ? FIXTURES.confirmed : FIXTURES[scenario],
      gets: 0,
      patches: 0,
      parentMounts: 0,
      failing: scenario === "server-error",
    };
    // staleTime keeps the seeded token and preferences from reaching the network; the booking query
    // still refetches on invalidation, which is what Save has to prove.
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
    });
    queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], OAUTH_TOKEN);
    queryClient.setQueryData(bookingDisplayPreferencesQueryKey, {
      ...inheritedBrowserBookingPreferences,
      timezoneMode: "CUSTOM",
      customTimezone: displayTimezone,
      overridden: true,
    });
    const id = server.document?.id ?? 999;
    const router = createRouter({
      routeTree: createBookingEventRouteTree(),
      history: createMemoryHistory({
        initialEntries: [`/booking/calendar/bookings/${id}${mode === "edit" ? "/edit" : ""}`],
      }),
    });
    return { queryClient, router, server };
  });

  return (
    <QueryClientProvider client={queryClient}>
      <NuqsAdapter>
        <I18nRoot namespaces={["booking", "common"]}>
          <ServerContext.Provider value={server}>
            <React.Suspense fallback={null}>
              <RouterProvider router={router as never} />
            </React.Suspense>
          </ServerContext.Provider>
        </I18nRoot>
      </NuqsAdapter>
    </QueryClientProvider>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Event Details Page",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance ---------------------------------------------------------------------------------------

const modeSwitchAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("Edit swaps the card contents inside the same mounted shell", async () => {
    await userEvent.click(await canvas.findByRole("button", { name: "Edit" }));
    await waitFor(() => expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/42/edit"));
    // The shell survived: heading, badges, and the header actions never remounted.
    expect(canvas.getByRole("heading", { name: "Confocal microscope" })).toBeInTheDocument();
    expect(canvas.getByText(/shell mounts: 1/)).toBeInTheDocument();
    expect(canvas.getByText(/Booking GETs: 1/)).toBeInTheDocument();
    expect(canvas.getByLabelText("Purpose")).toBeInTheDocument();
  });

  await step("Cancel editing discards the draft, sends no PATCH, and restores focus", async () => {
    await userEvent.type(canvas.getByLabelText("Purpose"), " CHANGED");
    await userEvent.click(canvas.getByRole("button", { name: "Cancel editing" }));
    await waitFor(() => expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/42"));
    expect(canvas.getByText(/PATCHes: 0/)).toBeInTheDocument();
    expect(canvas.queryByText(/CHANGED/)).not.toBeInTheDocument();
    await waitFor(() => expect(canvas.getByRole("button", { name: "Edit" })).toHaveFocus());
  });

  await step("Save refetches the booking once and returns to the refreshed readout", async () => {
    await userEvent.click(canvas.getByRole("button", { name: "Edit" }));
    await userEvent.type(canvas.getByLabelText("Purpose"), " Updated.");
    await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/42"));
    expect(canvas.getByText(/Booking GETs: 2 · PATCHes: 1 · shell mounts: 1/)).toBeInTheDocument();
    expect(canvas.getByText(/Updated\./)).toBeInTheDocument();
    expect(canvas.getByRole("status")).toHaveTextContent("Changes saved.");
  });
};

const dirtyGuardAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  // The guard's blocking behaviour is production code already covered by BookableItemPage's tests.
  // What is new here, and what this proves, is that a pathname-changing mode switch bypasses it.
  await step("an explicit mode exit bypasses the dirty-navigation guard", async () => {
    await userEvent.click(await canvas.findByRole("button", { name: "Edit" }));
    await userEvent.type(canvas.getByLabelText("Purpose"), " draft text");
    // The production DirtyNavigationGuard is mounted and armed by the dirty form.
    expect(canvas.getByText(/\/edit$/)).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Cancel editing" }));
    // An explicit mode exit bypasses the blocker rather than prompting.
    await waitFor(() => expect(canvas.queryByRole("alertdialog")).not.toBeInTheDocument());
    await waitFor(() => expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/42"));
  });
};

const cancelEventAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("cancelling shows the cancelled and removed states and drops every action", async () => {
    await userEvent.click(await canvas.findByRole("button", { name: "Cancel booking" }));
    await userEvent.click(await canvas.findByRole("button", { name: "Cancel booking" }));
    await waitFor(() => expect(canvas.getByText("Cancelled")).toBeInTheDocument());
    expect(canvas.getByText("Removed from booking lists")).toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: ".ics file" })).not.toBeInTheDocument();
    // Still on the same URL: a cancelled event stays readable from its direct link.
    expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/42");
  });
};

const roleLostAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("read-only details expose no capability the server withheld", async () => {
    expect(await canvas.findByRole("heading", { name: "Confocal microscope" })).toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: "Cancel booking" })).not.toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: ".ics file" })).not.toBeInTheDocument();
    expect(canvas.queryByRole("link")).not.toBeInTheDocument();
    // The return action falls back to My Bookings; no Calendar or bookable-item destination.
    expect(canvas.getByRole("button", { name: /Return to My Bookings/ })).toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: /Return to calendar/ })).not.toBeInTheDocument();
  });
};

const normalizeAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("/edit for a non-editable event normalizes to view mode with no form", async () => {
    await waitFor(() => expect(canvas.getByText(/^URL:/)).toHaveTextContent("/booking/calendar/bookings/43"));
    expect(canvas.queryByRole("button", { name: "Save changes" })).not.toBeInTheDocument();
    expect(canvas.queryByLabelText("Purpose")).not.toBeInTheDocument();
  });
};

const errorAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("the error state offers Retry and never shows server detail", async () => {
    expect(await canvas.findByText("Something went wrong")).toBeInTheDocument();
    expect(canvas.queryByText(/simulated server error/)).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
    expect(await canvas.findByRole("heading", { name: "Confocal microscope" })).toBeInTheDocument();
  });
};

export const ConfirmedBookingViewMode: Story = {
  args: { scenario: "confirmed" },
  play:
    import.meta.env.MODE === "test"
      ? async ({ canvasElement, step }) => {
          const canvas = within(canvasElement.ownerDocument.body);
          await step("metadata is separated from the booking details", async () => {
            const aside = await canvas.findByRole("complementary", { name: "About this booking" });
            expect(within(aside).getByText("Times shown in")).toBeInTheDocument();
            expect(within(aside).getByText("Europe/London")).toBeInTheDocument();
            expect(within(aside).getByText("Created")).toBeInTheDocument();
            expect(within(aside).getByText("Last updated")).toBeInTheDocument();

            const details = canvas.getByText("Purpose").closest("dl") as HTMLElement;
            expect(within(details).queryByText("Created")).not.toBeInTheDocument();
            expect(within(details).queryByText("Last updated")).not.toBeInTheDocument();

            // 08:00Z is 10:00 in Berlin but 09:00 in London, so the display zone is really applied.
            expect(canvas.getByText(/12 Sept 2026, 09:00/)).toBeInTheDocument();
          });
          await step("obsolete identifier and timezone labels stay gone", async () => {
            expect(canvas.queryByText("Booking id")).not.toBeInTheDocument();
            expect(canvas.queryByText("Timezone of instrument")).not.toBeInTheDocument();
            expect(canvas.queryByText("Displayed in")).not.toBeInTheDocument();
          });
        }
      : undefined,
};

export const DirectlyLoadedEditMode: Story = {
  args: { scenario: "confirmed", mode: "edit" },
};

export const ViewEditViewInteraction: Story = {
  args: { scenario: "confirmed" },
  play: import.meta.env.MODE === "test" ? modeSwitchAcceptance : undefined,
};

export const DirtyDraftGuard: Story = {
  args: { scenario: "confirmed" },
  play: import.meta.env.MODE === "test" ? dirtyGuardAcceptance : undefined,
};

export const CancelEventInteraction: Story = {
  args: { scenario: "confirmed" },
  play: import.meta.env.MODE === "test" ? cancelEventAcceptance : undefined,
};

export const RoleLostRequester: Story = {
  args: { scenario: "role-lost" },
  play: import.meta.env.MODE === "test" ? roleLostAcceptance : undefined,
};

/** A readable but non-editable event opened at /edit must not expose a form. */
export const NonEditableOpenedAtEditUrl: Story = {
  args: { scenario: "role-lost", mode: "edit" },
  play: import.meta.env.MODE === "test" ? normalizeAcceptance : undefined,
};

/** The metadata aside still names the display zone when it matches the instrument's. */
export const DisplayTimezoneMatchesInstrument: Story = {
  args: { scenario: "confirmed", displayTimezone: "Europe/Berlin" },
  play:
    import.meta.env.MODE === "test"
      ? async ({ canvasElement, step }) => {
          const canvas = within(canvasElement.ownerDocument.body);
          await step("the aside names the display zone and the times use it", async () => {
            const aside = await canvas.findByRole("complementary", { name: "About this booking" });
            expect(within(aside).getByText("Times shown in")).toBeInTheDocument();
            expect(within(aside).getByText("Europe/Berlin")).toBeInTheDocument();
            expect(canvas.getByText(/12 Sept 2026, 10:00/)).toBeInTheDocument();
          });
        }
      : undefined,
};

export const ConfirmedMaintenanceEvent: Story = {
  args: { scenario: "maintenance" },
};

export const CancelledAndRemoved: Story = {
  args: { scenario: "cancelled" },
};

/** Cancelled without soft deletion: the Cancelled badge appears without the removal badge. */
export const CancelledButStillListed: Story = {
  args: { scenario: "cancelled-non-deleted" },
};

/** Confirmed but soft-deleted: removal badge, and no edit, cancel, or export action. */
export const ConfirmedButRemovedFromLists: Story = {
  args: { scenario: "confirmed-deleted" },
};

/** Archived rejects editing but keeps cancellation and export for an eligible future event. */
export const ArchivedConfigurationFutureEvent: Story = {
  args: { scenario: "archived" },
};

export const ConcealedNotFound: Story = {
  args: { scenario: "not-found" },
};

export const ServerErrorWithRetry: Story = {
  args: { scenario: "server-error" },
  play: import.meta.env.MODE === "test" ? errorAcceptance : undefined,
};
