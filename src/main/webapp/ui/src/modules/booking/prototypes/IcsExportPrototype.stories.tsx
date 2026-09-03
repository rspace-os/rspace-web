// PROTOTYPE ONLY (RPD-183 issue 22). Where a one-off .ics download for a single booking should
// live, and how it stays distinct from a live calendar subscription. Three placements:
// BookingDetailsOnly hosts the action solely on a booking-details card; DetailsAndMyBookings adds
// it to a My Bookings row-action menu (the production seam is TableList's rowActions — imitated
// here with a plain table because TableList needs its collection machinery); CalendarEventPopover
// hosts it inside the production DayTimelineEventCard via its renderEventActions seam, closing the
// card with the shared ActionBar, with the details card as fallback. The action reads as an icon
// plus ".ics file" everywhere, so the three placements differ only in where it sits.
// Downloads are simulated in memory with a short delay so progress,
// repeat-activation guarding, and failure recovery are observable; no file is produced and no
// network runs. The subscription contrast panel is static copy — the real
// CalendarSubscriptionPopover fetches over React Query and has no MSW wiring in Storybook.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarArrowDownIcon, EllipsisVerticalIcon, InfoIcon } from "lucide-react";
import * as React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { ActionBar } from "@/modules/common/ui/action-bar";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Label } from "@/modules/common/ui/label";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Spinner } from "@/modules/common/ui/spinner";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { cn } from "@/modules/common/utils/cn";

type Placement = "details" | "my-bookings" | "event-popover";

// --- Fixtures -----------------------------------------------------------------------------------

type BookingFixture = {
  id: string;
  /** Withheld in the UI when privacy is "busy". */
  title: string;
  requester: string;
  itemName: string;
  globalId: string;
  dateLabel: string;
  isoDate: string;
  timeLabel: string;
  status: "future" | "past" | "cancelled";
  privacy: "full" | "busy";
  archivedConfiguration: boolean;
  /** The simulated request fails once, then succeeds. */
  failDownload: boolean;
};

const booking = (
  partial: Partial<BookingFixture> & Pick<BookingFixture, "id" | "title" | "dateLabel" | "isoDate">,
): BookingFixture => ({
  requester: "Ada Lovelace",
  itemName: "Confocal microscope",
  globalId: "IN123",
  timeLabel: "10:00 – 12:00",
  status: "future",
  privacy: "full",
  archivedConfiguration: false,
  failDownload: false,
  ...partial,
});

const BOOKINGS: readonly BookingFixture[] = [
  booking({ id: "b1", title: "Laser alignment", dateLabel: "12 Sep 2026", isoDate: "2026-09-12" }),
  booking({ id: "b2", title: "Sample imaging", dateLabel: "20 Aug 2026", isoDate: "2026-08-20", status: "past" }),
  booking({
    id: "b3",
    title: "Cancelled training session",
    dateLabel: "18 Sep 2026",
    isoDate: "2026-09-18",
    status: "cancelled",
  }),
  booking({
    id: "b4",
    title: "Private booking",
    requester: "Another requester",
    dateLabel: "14 Sep 2026",
    isoDate: "2026-09-14",
    privacy: "busy",
  }),
  booking({
    id: "b5",
    title: "Filter check",
    itemName: "Retired spectrometer",
    globalId: "IN077",
    dateLabel: "16 Sep 2026",
    isoDate: "2026-09-16",
    archivedConfiguration: true,
  }),
  booking({
    id: "b6",
    title: "Detector run (simulated failure)",
    dateLabel: "19 Sep 2026",
    isoDate: "2026-09-19",
    failDownload: true,
  }),
];

/** The proposed filename contract: item slug, global id, booking start date. */
function filenameOf(fixture: BookingFixture): string {
  const slug = fixture.itemName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  return `${slug}-${fixture.globalId}-${fixture.isoDate}.ics`;
}

// --- Simulated download state -------------------------------------------------------------------

type DownloadPhase = "pending" | "failed" | "done";

function useSimulatedDownloads() {
  const [phases, setPhases] = React.useState<Record<string, DownloadPhase>>({});
  const [status, setStatus] = React.useState("");
  const [error, setError] = React.useState("");
  const failedOnce = React.useRef<Set<string>>(new Set());

  const start = (fixture: BookingFixture) => {
    if (phases[fixture.id] === "pending") return;
    setError("");
    setPhases((current) => ({ ...current, [fixture.id]: "pending" }));
    setStatus(`Preparing calendar file for ${fixture.itemName}, ${fixture.dateLabel}…`);
    window.setTimeout(() => {
      if (fixture.failDownload && !failedOnce.current.has(fixture.id)) {
        failedOnce.current.add(fixture.id);
        setPhases((current) => ({ ...current, [fixture.id]: "failed" }));
        setStatus("");
        setError(`Download failed for ${fixture.itemName}, ${fixture.dateLabel}. Nothing was saved; try again.`);
        return;
      }
      setPhases((current) => ({ ...current, [fixture.id]: "done" }));
      setStatus(`Downloaded ${filenameOf(fixture)}. No subscription was created or changed.`);
    }, 600);
  };

  return { phases, status, error, start };
}

type Downloads = ReturnType<typeof useSimulatedDownloads>;

/** Why the action is unavailable, or null when it may run. */
function blockedReason(fixture: BookingFixture, allowPastExport: boolean): string | null {
  if (fixture.status === "cancelled") {
    return "This booking is cancelled. A file would add an event that no longer exists.";
  }
  if (fixture.status === "past" && !allowPastExport) {
    return "Historical export is turned off in this variant (Product decision pending).";
  }
  return null;
}

function DownloadButton({
  fixture,
  downloads,
  allowPastExport,
  size = "sm",
}: {
  fixture: BookingFixture;
  downloads: Downloads;
  allowPastExport: boolean;
  size?: "sm" | "default";
}) {
  const reasonId = React.useId();
  const phase = downloads.phases[fixture.id];
  const reason = blockedReason(fixture, allowPastExport) ?? (phase === "pending" ? "Download in progress." : null);
  return (
    <span className="inline-flex min-w-0 flex-col items-start gap-1">
      <Button
        type="button"
        variant="outline"
        size={size}
        // The visible label is the whole label, so the accessible name has to open with it
        // (WCAG 2.5.3); the booking follows it because a page shows several of these.
        aria-label={`.ics file for ${fixture.itemName}, ${fixture.dateLabel}`}
        aria-disabled={reason === null ? undefined : true}
        aria-describedby={reason === null ? undefined : reasonId}
        className={cn("min-h-8", reason !== null && "cursor-not-allowed opacity-50")}
        onClick={(event) => {
          if (reason !== null) {
            event.preventDefault();
            return;
          }
          downloads.start(fixture);
        }}
      >
        {/* The label stays put while pending; only the icon spins, so the row never reflows. */}
        {phase === "pending" ? (
          <Spinner className="size-4" aria-hidden="true" />
        ) : (
          <CalendarArrowDownIcon aria-hidden="true" />
        )}
        .ics file
      </Button>
      {reason !== null ? (
        <span id={reasonId} className="text-xs text-muted-foreground">
          {reason}
        </span>
      ) : null}
    </span>
  );
}

// --- Shared page chrome ---------------------------------------------------------------------------

/** One booking, presented the way the merged item page's booking details read. */
function BookingDetailsCard({
  fixture,
  downloads,
  allowPastExport,
}: {
  fixture: BookingFixture;
  downloads: Downloads;
  allowPastExport: boolean;
}) {
  const isPrivate = fixture.privacy === "busy";
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          {isPrivate ? "Busy (details private)" : fixture.title}
          {fixture.status === "cancelled" ? <Badge variant="destructive">Cancelled</Badge> : null}
          {fixture.status === "past" ? <Badge variant="secondary">Past</Badge> : null}
          {fixture.archivedConfiguration ? <Badge variant="outline">Configuration archived</Badge> : null}
        </CardTitle>
        <CardDescription>
          {fixture.itemName} ({fixture.globalId}) · {fixture.dateLabel} · {fixture.timeLabel}
          {isPrivate ? " · booked by someone else" : ` · booked by ${fixture.requester}`}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-1.5">
        <DownloadButton fixture={fixture} downloads={downloads} allowPastExport={allowPastExport} />
        {isPrivate ? (
          <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
            <InfoIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            The file follows the privacy projection: it contains only the reserved time and the item, not the requester
            or purpose.
          </p>
        ) : null}
        {fixture.archivedConfiguration ? (
          <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
            <InfoIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            Existing bookings stay exportable after archiving. Creating a new subscription link is what archiving
            disables.
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

/** The one-off file versus a live subscription, in the words each surface would use. */
function LanguageContrastCard() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">One-off file, not a subscription</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="list-disc space-y-1 pl-4 text-xs text-muted-foreground">
          <li>.ics file: this one booking, saved once. It never updates itself.</li>
          <li>Calendar subscription: a private URL your calendar app checks for changes over time.</li>
          <li>
            Opening the file in Apple Calendar, Google Calendar, or Outlook is up to that app; RSpace does not control
            what happens after the download.
          </li>
        </ul>
      </CardContent>
    </Card>
  );
}

function StatusRegion({ downloads }: { downloads: Downloads }) {
  return (
    <>
      {downloads.status !== "" ? (
        <p role="status" className="text-xs text-muted-foreground">
          {downloads.status}
        </p>
      ) : null}
      {downloads.error !== "" ? (
        <Alert variant="destructive">
          <AlertTitle>Download failed</AlertTitle>
          <AlertDescription>{downloads.error}</AlertDescription>
        </Alert>
      ) : null}
    </>
  );
}

// --- Placement variants ---------------------------------------------------------------------------

function MyBookingsImitation({ downloads, allowPastExport }: { downloads: Downloads; allowPastExport: boolean }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">My Bookings</CardTitle>
        <CardDescription>
          Row layout imitates the production TableList; the real insertion point is its rowActions column, which needs a
          per-row capability field to render the action.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Item</TableHead>
              <TableHead>Date</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {BOOKINGS.map((fixture) => (
              <TableRow key={fixture.id}>
                <TableCell className="whitespace-normal">
                  {fixture.itemName} ({fixture.globalId})
                </TableCell>
                <TableCell>{fixture.dateLabel}</TableCell>
                <TableCell className="capitalize">{fixture.status}</TableCell>
                <TableCell>
                  <Menu>
                    <MenuTrigger
                      render={<Button type="button" size="icon-sm" variant="ghost" />}
                      aria-label={`Actions for ${fixture.itemName}, ${fixture.dateLabel}`}
                    >
                      <EllipsisVerticalIcon aria-hidden="true" />
                    </MenuTrigger>
                    <MenuContent align="end">
                      <MenuItem>View details</MenuItem>
                      <MenuItem
                        disabled={blockedReason(fixture, allowPastExport) !== null}
                        onClick={() => downloads.start(fixture)}
                      >
                        <CalendarArrowDownIcon aria-hidden="true" />
                        .ics file
                      </MenuItem>
                    </MenuContent>
                  </Menu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

/**
 * The card view closes with the shared ActionBar rather than a bespoke row, so the export sits
 * beside the actions the production card already offers and inherits its overflow, hairlines, and
 * 44 px targets. Two trade-offs the ActionBar API imposes here: the accessible name is the visible
 * label alone (no per-action aria-label), and a blocked action is truly disabled rather than
 * aria-disabled, so it drops out of the tab order.
 */
function EventCardActions({
  fixture,
  downloads,
  allowPastExport,
}: {
  fixture: BookingFixture;
  downloads: Downloads;
  allowPastExport: boolean;
}) {
  const pending = downloads.phases[fixture.id] === "pending";
  return (
    <ActionBar
      actions={[
        { label: "View details" },
        {
          label: ".ics file",
          icon: pending ? Spinner : CalendarArrowDownIcon,
          disabled: pending || blockedReason(fixture, allowPastExport) !== null,
          // Never overflowed: the placement question is whether the export is findable here.
          alwaysVisible: true,
          onClick: () => downloads.start(fixture),
        },
      ]}
    />
  );
}

function EventPopoverVariant({ downloads, allowPastExport }: { downloads: Downloads; allowPastExport: boolean }) {
  const full = BOOKINGS[0];
  const busy = BOOKINGS[3];
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Calendar events</CardTitle>
        <CardDescription>
          The production DayTimelineEventCard with the download action injected through its renderEventActions seam. A
          private busy event exposes no details popover, so booking details remain the fallback host.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-start gap-3">
        <div className="w-64">
          <DayTimelineEventCard
            event={{
              id: full.id,
              kind: "booking",
              privacy: "full",
              title: full.title,
              bookedBy: full.requester,
              canEdit: false,
              startMinute: 10 * 60,
              endMinute: 12 * 60,
              item: { name: full.itemName, globalId: full.globalId },
              notes: "Quarterly maintenance run.",
            }}
            date={full.isoDate}
            renderEventActions={(event) => (
              <EventCardActions
                fixture={BOOKINGS.find((candidate) => candidate.id === event.id) ?? full}
                downloads={downloads}
                allowPastExport={allowPastExport}
              />
            )}
          />
        </div>
        <div className="w-64">
          <DayTimelineEventCard
            event={{
              id: busy.id,
              kind: "booking",
              privacy: "busy",
              startMinute: 9 * 60,
              endMinute: 10 * 60,
            }}
            date={busy.isoDate}
          />
        </div>
      </CardContent>
    </Card>
  );
}

// --- The page --------------------------------------------------------------------------------------

function IcsExportPrototype({ placement }: { placement: Placement }) {
  const downloads = useSimulatedDownloads();
  const [allowPastExport, setAllowPastExport] = React.useState(false);
  const [narrow, setNarrow] = React.useState(false);

  return (
    <div className={cn("space-y-4 p-4", narrow && "max-w-[320px]")}>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span className="flex items-center gap-2">
          <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
          <Label htmlFor="narrow" className="text-xs">
            Constrain page to 320 px
          </Label>
        </span>
        <span className="flex items-center gap-2">
          <Checkbox
            id="allow-past"
            checked={allowPastExport}
            onCheckedChange={(checked) => setAllowPastExport(checked === true)}
          />
          <Label htmlFor="allow-past" className="text-xs">
            Allow historical export (both treatments prototyped)
          </Label>
        </span>
      </div>

      {placement === "details" ? (
        <div className="space-y-3">
          {BOOKINGS.map((fixture) => (
            <BookingDetailsCard
              key={fixture.id}
              fixture={fixture}
              downloads={downloads}
              allowPastExport={allowPastExport}
            />
          ))}
        </div>
      ) : null}

      {placement === "my-bookings" ? (
        <div className="space-y-3">
          <BookingDetailsCard fixture={BOOKINGS[0]} downloads={downloads} allowPastExport={allowPastExport} />
          <MyBookingsImitation downloads={downloads} allowPastExport={allowPastExport} />
        </div>
      ) : null}

      {placement === "event-popover" ? (
        <div className="space-y-3">
          <EventPopoverVariant downloads={downloads} allowPastExport={allowPastExport} />
          <BookingDetailsCard fixture={BOOKINGS[3]} downloads={downloads} allowPastExport={allowPastExport} />
        </div>
      ) : null}

      <LanguageContrastCard />
      <StatusRegion downloads={downloads} />
    </div>
  );
}

function PrototypePage({ placement }: { placement: Placement }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <IcsExportPrototype placement={placement} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/One-off ICS Export",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance -------------------------------------------------------------------------------------

const downloadName = (fixture: BookingFixture) => `.ics file for ${fixture.itemName}, ${fixture.dateLabel}`;

/** The card view goes through the shared ActionBar, whose accessible name is the label alone. */
const CARD_DOWNLOAD_NAME = ".ics file";

const runSharedAcceptance = async (
  canvasElement: HTMLElement,
  step: Parameters<NonNullable<Story["play"]>>[0]["step"],
) => {
  const canvas = within(canvasElement.ownerDocument.body);
  const future = BOOKINGS[0];
  const trigger = await canvas.findByRole("button", { name: downloadName(future) });

  await step("the target is at least 24 by 24 CSS pixels", async () => {
    const rect = trigger.getBoundingClientRect();
    expect(rect.width).toBeGreaterThanOrEqual(24);
    expect(rect.height).toBeGreaterThanOrEqual(24);
  });

  await step("progress is announced, repeats are guarded, and focus survives", async () => {
    trigger.focus();
    await userEvent.click(trigger);
    expect(await canvas.findByRole("status")).toHaveTextContent("Preparing calendar file");
    expect(trigger).toHaveAttribute("aria-disabled", "true");
    // A second activation while pending must not restart or duplicate the download.
    await userEvent.click(trigger);
    await waitFor(() =>
      expect(canvas.getByRole("status")).toHaveTextContent(
        "Downloaded confocal-microscope-IN123-2026-09-12.ics. No subscription was created or changed.",
      ),
    );
    expect(canvasElement.ownerDocument.activeElement).toBe(trigger);
  });

  await step("320 px keeps every control reachable without horizontal overflow", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
    const page = canvasElement.querySelector(".space-y-4") as HTMLElement;
    await waitFor(() => expect(page.getBoundingClientRect().width).toBeLessThanOrEqual(320));
    expect(page.scrollWidth).toBeLessThanOrEqual(page.clientWidth + 1);
  });
};

const detailsAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await runSharedAcceptance(canvasElement, step);

  await step("a cancelled booking explains itself instead of tempting a click", async () => {
    const cancelled = canvas.getByRole("button", { name: downloadName(BOOKINGS[2]) });
    expect(cancelled).toHaveAttribute("aria-disabled", "true");
    expect(
      canvas.getByText("This booking is cancelled. A file would add an event that no longer exists."),
    ).toBeInTheDocument();
    await userEvent.click(cancelled);
    expect(canvas.queryByText(/Preparing calendar file for Confocal microscope, 18 Sep/)).not.toBeInTheDocument();
  });

  await step("historical export is prototyped in both treatments", async () => {
    const past = canvas.getByRole("button", { name: downloadName(BOOKINGS[1]) });
    expect(past).toHaveAttribute("aria-disabled", "true");
    await userEvent.click(canvas.getByRole("checkbox", { name: /Allow historical export/ }));
    await waitFor(() => expect(past).not.toHaveAttribute("aria-disabled"));
  });

  await step("a failure reports without losing focus, and a retry succeeds", async () => {
    const failing = canvas.getByRole("button", { name: downloadName(BOOKINGS[5]) });
    failing.focus();
    await userEvent.click(failing);
    expect(await canvas.findByRole("alert")).toHaveTextContent("Download failed");
    expect(canvasElement.ownerDocument.activeElement).toBe(failing);
    await userEvent.click(failing);
    await waitFor(() =>
      expect(canvas.getByRole("status")).toHaveTextContent("confocal-microscope-IN123-2026-09-19.ics"),
    );
  });
};

const myBookingsAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await runSharedAcceptance(canvasElement, step);

  await step("the row action menu names its booking and downloads once", async () => {
    await userEvent.click(canvas.getByRole("button", { name: "Actions for Retired spectrometer, 16 Sep 2026" }));
    await userEvent.click(await canvas.findByRole("menuitem", { name: ".ics file" }));
    await waitFor(() =>
      expect(canvas.getByRole("status")).toHaveTextContent("retired-spectrometer-IN077-2026-09-16.ics"),
    );
    await waitFor(() => expect(canvas.queryByRole("menu")).not.toBeInTheDocument());
  });
};

const eventPopoverAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("the expanded event card hosts the download action", async () => {
    const toggle = await canvas.findByRole("button", { name: /Show details for Laser alignment/ });
    await userEvent.click(toggle);
    const download = await canvas.findByRole("button", { name: CARD_DOWNLOAD_NAME });
    // The export shares the ActionBar with the card's existing action rather than replacing it.
    expect(canvas.getByRole("button", { name: "View details" })).toBeInTheDocument();
    await userEvent.click(download);
    await waitFor(() =>
      expect(canvas.getByRole("status")).toHaveTextContent("confocal-microscope-IN123-2026-09-12.ics"),
    );
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(canvas.queryByRole("button", { name: CARD_DOWNLOAD_NAME })).not.toBeInTheDocument());
  });

  await step("a private event falls back to the details card with the privacy projection", async () => {
    const fallback = canvas.getByRole("button", { name: downloadName(BOOKINGS[3]) });
    expect(fallback).toBeInTheDocument();
    expect(canvas.getByText(/contains only the reserved time and the item/)).toBeInTheDocument();
  });

  await step("320 px keeps every control reachable without horizontal overflow", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
    const page = canvasElement.querySelector(".space-y-4") as HTMLElement;
    await waitFor(() => expect(page.getBoundingClientRect().width).toBeLessThanOrEqual(320));
    expect(page.scrollWidth).toBeLessThanOrEqual(page.clientWidth + 1);
  });
};

export const BookingDetailsOnly: Story = {
  args: { placement: "details" },
  play: import.meta.env.MODE === "test" ? detailsAcceptance : undefined,
};

export const DetailsAndMyBookings: Story = {
  args: { placement: "my-bookings" },
  play: import.meta.env.MODE === "test" ? myBookingsAcceptance : undefined,
};

export const CalendarEventPopover: Story = {
  args: { placement: "event-popover" },
  play: import.meta.env.MODE === "test" ? eventPopoverAcceptance : undefined,
};
