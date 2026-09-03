// PROTOTYPE ONLY (booking-event-details-page plan, .claude/booking-event-details-page-plan.md).
// BookingTimestampPlacementPrototype asks WHERE "Created" and "Last updated" sit. This file asks
// HOW they read once a place is chosen. Four variants, one per story, over the same fixture:
//   RelativeWithAbsolute   "2 days ago" first, the absolute instant visible beside it
//   AttributionByline      person and time bound together: "Booked by Ada on 30 Aug"
//   ExplicitUnchanged      an unedited event says so instead of hiding the second line
//   ActivityTimeline       created and updated placed on a strip that ends at the booking itself
// Two facts shape every variant:
//   - the booking document has createdBy and bookedBy but no updatedBy, so nothing may claim who
//     edited; AttributionByline names the creator only and marks the gap in its comment;
//   - relative times are formatted against a PINNED reference instant, not Date.now(), so the play
//     assertions and screenshots do not drift by the hour. Production would use the aligned minute.
// Same toggles as the placement study (never edited, 320 px) so the two files compare directly.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarCheckIcon, PencilLineIcon, PlusIcon } from "lucide-react";
import * as React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

type Display = "relative" | "attribution" | "unchanged" | "timeline";

// --- Fixture -----------------------------------------------------------------------------------

const EVENT = {
  target: { name: "Confocal microscope", globalId: "IN123" },
  start: "2026-09-12T08:00:00Z",
  end: "2026-09-12T10:00:00Z",
  bookedBy: "Ada Lovelace (ada)",
  createdBy: "Ada Lovelace (ada)",
  purpose: "Laser alignment ahead of the quarterly imaging run.\nBring the calibration slides.",
  createdAt: "2026-08-30T07:14:00Z",
  updatedAt: "2026-08-31T14:40:00Z",
};

/** Pinned "now": two days after the edit, twelve before the booking. */
const NOW = new Date("2026-09-02T09:00:00Z");
const DISPLAY_TIMEZONE = "Europe/London";

const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: DISPLAY_TIMEZONE }).format(
    new Date(value),
  );
const dateOnly = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: DISPLAY_TIMEZONE }).format(new Date(value));

const RELATIVE = new Intl.RelativeTimeFormat("en-GB", { numeric: "auto" });
const DAY = 86_400_000;
/** Whole days is enough for audit metadata; hours only matter on the day itself. */
const relative = (value: string) => {
  const days = Math.round((new Date(value).getTime() - NOW.getTime()) / DAY);
  if (days === 0) return RELATIVE.format(Math.round((new Date(value).getTime() - NOW.getTime()) / 3_600_000), "hour");
  return RELATIVE.format(days, "day");
};

type Timestamps = { createdAt: string; updatedAt: string; edited: boolean };

const timestampsFor = (neverEdited: boolean): Timestamps => ({
  createdAt: EVENT.createdAt,
  updatedAt: neverEdited ? EVENT.createdAt : EVENT.updatedAt,
  edited: !neverEdited,
});

// --- The four displays ------------------------------------------------------------------------------

/** Relative first for scanning, absolute beside it so nobody has to hover or focus to get it. */
function RelativeTime({ value }: { value: string }) {
  return (
    <time dateTime={value}>
      {relative(value)}
      <span className="text-muted-foreground">{` (${dateTime(value)})`}</span>
    </time>
  );
}

function RelativeRows({ timestamps }: { timestamps: Timestamps }) {
  return (
    <>
      <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
        <dt className="font-medium">Created</dt>
        <dd className="min-w-0">
          <RelativeTime value={timestamps.createdAt} />
        </dd>
      </div>
      {timestamps.edited ? (
        <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
          <dt className="font-medium">Last updated</dt>
          <dd className="min-w-0">
            <RelativeTime value={timestamps.updatedAt} />
          </dd>
        </div>
      ) : null}
    </>
  );
}

/**
 * Replaces the separate "Booked by" row: the person and the moment become one statement. There is
 * no updatedBy on the document, so the edit keeps its time but gets no name.
 */
function AttributionBylineFooter({ timestamps }: { timestamps: Timestamps }) {
  return (
    <CardFooter className="border-t pt-4 text-sm">
      <p data-slot="timestamps" className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
        <span>Booked by</span>
        <UserBadge name={EVENT.createdBy} />
        <span>
          on <time dateTime={timestamps.createdAt}>{dateTime(timestamps.createdAt)}</time>
        </span>
        {timestamps.edited ? (
          <span className="text-muted-foreground">
            {"· edited "}
            <time dateTime={timestamps.updatedAt}>{dateTime(timestamps.updatedAt)}</time>
          </span>
        ) : null}
      </p>
    </CardFooter>
  );
}

/** The other variants hide "updated" when it repeats "created". This one says why it is missing. */
function ExplicitUnchangedRows({ timestamps }: { timestamps: Timestamps }) {
  return (
    <>
      <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
        <dt className="font-medium">Created</dt>
        <dd className="min-w-0">
          <time dateTime={timestamps.createdAt}>{dateTime(timestamps.createdAt)}</time>
        </dd>
      </div>
      <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
        <dt className="font-medium">Last updated</dt>
        <dd className="min-w-0">
          {timestamps.edited ? (
            <time dateTime={timestamps.updatedAt}>{dateTime(timestamps.updatedAt)}</time>
          ) : (
            <span className="text-muted-foreground">Unchanged since creation</span>
          )}
        </dd>
      </div>
    </>
  );
}

/** Three dots on one line: the audit instants get meaning from their distance to the booking. */
function ActivityTimelineFooter({ timestamps }: { timestamps: Timestamps }) {
  const steps = [
    { icon: PlusIcon, label: "Created", at: timestamps.createdAt },
    ...(timestamps.edited ? [{ icon: PencilLineIcon, label: "Updated", at: timestamps.updatedAt }] : []),
    { icon: CalendarCheckIcon, label: "Starts", at: EVENT.start },
  ];
  return (
    // Container queries, not viewport ones: the 320 px toggle narrows the card, not the window.
    <CardFooter className="@container border-t pt-4">
      <ol data-slot="timestamps" className="flex w-full min-w-0 flex-col gap-3 text-sm @sm:flex-row @sm:gap-4">
        {steps.map((step, index) => (
          <li
            key={step.label}
            className={cn(
              "relative flex min-w-0 items-start gap-2 @sm:flex-1 @sm:flex-col @sm:gap-1",
              // ponytail: the connector is a border on each step after the first; a real component would draw one line.
              index > 0 && "border-l pl-3 @sm:border-t @sm:border-l-0 @sm:pt-3 @sm:pl-0",
            )}
          >
            <step.icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <span className="min-w-0">
              <span className="block font-medium">{step.label}</span>
              <time dateTime={step.at} className="block text-muted-foreground">
                {dateOnly(step.at)}
              </time>
            </span>
          </li>
        ))}
      </ol>
    </CardFooter>
  );
}

// --- The page the displays sit inside -----------------------------------------------------------------

function TimestampDisplayPrototype({ display }: { display: Display }) {
  const [neverEdited, setNeverEdited] = React.useState(false);
  const [narrow, setNarrow] = React.useState(false);
  const timestamps = timestampsFor(neverEdited);
  const inList = display === "relative" || display === "unchanged";

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span className="flex items-center gap-2">
          <Checkbox
            id="never-edited"
            checked={neverEdited}
            onCheckedChange={(checked) => setNeverEdited(checked === true)}
          />
          <Label htmlFor="never-edited" className="text-xs">
            Never edited (updated repeats created)
          </Label>
        </span>
        <span className="flex items-center gap-2">
          <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
          <Label htmlFor="narrow" className="text-xs">
            Constrain page to 320 px
          </Label>
        </span>
      </div>

      <div className={cn("space-y-6", narrow && "max-w-[320px]")}>
        <section className="flex flex-wrap items-center gap-4">
          <InventoryItem
            name={EVENT.target.name}
            nameAs="h1"
            globalId={EVENT.target.globalId}
            idPlacement="title"
            className="min-w-full flex-1 p-0 sm:min-w-0"
          />
          <div className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0">
            <Badge variant="outline">Booking</Badge>
            <Badge>Confirmed</Badge>
          </div>
        </section>

        <Card>
          <CardHeader>
            <CardTitle>Booking details</CardTitle>
          </CardHeader>
          <CardContent>
            <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
              <dl
                className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}
                {...(inList ? { "data-slot": "timestamps" } : {})}
              >
                <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                  <dt className="font-medium">When</dt>
                  <dd className="min-w-0">
                    <time dateTime={EVENT.start}>{dateTime(EVENT.start)}</time>
                    {" – "}
                    <time dateTime={EVENT.end}>{dateTime(EVENT.end)}</time>
                  </dd>
                </div>
                {display === "attribution" ? null : (
                  <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                    <dt className="font-medium">Booked by</dt>
                    <dd className="min-w-0">
                      <UserBadge name={EVENT.bookedBy} />
                    </dd>
                  </div>
                )}
                <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                  <dt className="font-medium">Purpose</dt>
                  <dd className="min-w-0 whitespace-pre-line">{EVENT.purpose}</dd>
                </div>
                {display === "relative" ? <RelativeRows timestamps={timestamps} /> : null}
                {display === "unchanged" ? <ExplicitUnchangedRows timestamps={timestamps} /> : null}
              </dl>
            </div>
          </CardContent>
          {display === "attribution" ? <AttributionBylineFooter timestamps={timestamps} /> : null}
          {display === "timeline" ? <ActivityTimelineFooter timestamps={timestamps} /> : null}
        </Card>
      </div>
    </div>
  );
}

function PrototypePage({ display }: { display: Display }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <TimestampDisplayPrototype display={display} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Timestamp Display",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance ----------------------------------------------------------------------------------

type Step = Parameters<NonNullable<Story["play"]>>[0]["step"];

const region = async (canvasElement: HTMLElement) => {
  let found: HTMLElement | null = null;
  await waitFor(() => {
    found = canvasElement.ownerDocument.querySelector('[data-slot="timestamps"]');
    expect(found).not.toBeNull();
  });
  return found as unknown as HTMLElement;
};

/** Same bar as the placement study, so the two files are judged on equal terms. */
const sharedAcceptance = async (canvasElement: HTMLElement, step: Step) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("an unedited event does not show the same instant twice", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
    const times = within(await region(canvasElement)).getAllByText(/30 Aug 2026/);
    expect(times).toHaveLength(1);
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
  });

  await step("320 px keeps the timestamps readable without horizontal overflow", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
    const found = await region(canvasElement);
    expect(found.scrollWidth).toBeLessThanOrEqual(found.clientWidth + 1);
  });
};

const relativeAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  await step("relative wording leads and the absolute instant stays visible", async () => {
    const found = await region(canvasElement);
    expect(found).toHaveTextContent(/Created\s*3 days ago \(30 Aug 2026, 08:14\)/);
    expect(found).toHaveTextContent(/Last updated\s*2 days ago \(31 Aug 2026, 15:40\)/);
  });
  await sharedAcceptance(canvasElement, step);
};

const attributionAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await step("the creator is named once, in the byline, and the edit names nobody", async () => {
    const list = canvas.getByText("Purpose").closest("dl") as HTMLElement;
    expect(within(list).queryByText("Booked by")).not.toBeInTheDocument();
    const found = await region(canvasElement);
    // The UserBadge prefixes its initials, so match the name after them.
    expect(found).toHaveTextContent(/^Booked by\s*AL\s*Ada Lovelace \(ada\)\s*on 30 Aug 2026, 08:14/);
    expect(found).toHaveTextContent("· edited 31 Aug 2026, 15:40");
    expect(found).not.toHaveTextContent(/edited by/i);
  });
  await sharedAcceptance(canvasElement, step);
};

const unchangedAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await step("an unedited event keeps the row and explains the absence", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
    expect(await canvas.findByText("Unchanged since creation")).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
    expect(canvas.queryByText("Unchanged since creation")).not.toBeInTheDocument();
  });
  await sharedAcceptance(canvasElement, step);
};

const timelineAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  await step("the strip runs from creation to the booking itself", async () => {
    const items = within(await region(canvasElement)).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      "Created30 Aug 2026",
      "Updated31 Aug 2026",
      "Starts12 Sept 2026",
    ]);
  });
  await sharedAcceptance(canvasElement, step);
};

/** "2 days ago" first, the absolute instant beside it. */
export const RelativeWithAbsolute: Story = {
  args: { display: "relative" },
  play: import.meta.env.MODE === "test" ? relativeAcceptance : undefined,
};

/** Person and moment as one sentence; replaces the "Booked by" row. */
export const AttributionByline: Story = {
  args: { display: "attribution" },
  play: import.meta.env.MODE === "test" ? attributionAcceptance : undefined,
};

/** An unedited event says "Unchanged since creation" rather than losing a row. */
export const ExplicitUnchanged: Story = {
  args: { display: "unchanged" },
  play: import.meta.env.MODE === "test" ? unchangedAcceptance : undefined,
};

/** Created and updated on a strip that ends at the booking's start. */
export const ActivityTimeline: Story = {
  args: { display: "timeline" },
  play: import.meta.env.MODE === "test" ? timelineAcceptance : undefined,
};
