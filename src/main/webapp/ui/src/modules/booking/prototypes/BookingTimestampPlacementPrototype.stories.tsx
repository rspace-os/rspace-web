// PROTOTYPE ONLY (booking-event-details-page plan, .claude/booking-event-details-page-plan.md).
// Where should "Created" and "Last updated" sit on the booking event details page? Plan step 4 puts
// them at items 9 and 10 of the definition list, which gives audit metadata the same weight as the
// booking's own facts and pushes Purpose further up the page. Four structurally different
// placements, one per story, over the same fixture and the same details card:
//   DefinitionListRows  the plan as written, kept as the control
//   CardFooterByline    one muted sentence under a hairline, outside the scanning path
//   HeaderSubtitle      provenance beside the item identity, above the card
//   HistoryDisclosure   collapsed behind a History toggle, with room for a later audit trail
// The two toggles change what the placements have to cope with: an event that was never edited
// (where "updated" is noise, since it merely repeats "created") and a 320 px viewport.
// This is a placement study only: no router, no query, no network. The surrounding page is trimmed
// to the parts that compete for the same space, so the variants are judged in context rather than
// in a vacuum; the full page lives in BookingEventDetailsPrototype.stories.tsx.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { HistoryIcon } from "lucide-react";
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

type Placement = "rows" | "footer" | "header" | "disclosure";

// --- Fixture -----------------------------------------------------------------------------------

const EVENT = {
  target: { name: "Confocal microscope", globalId: "IN123" },
  start: "2026-09-12T08:00:00Z",
  end: "2026-09-12T10:00:00Z",
  bookedBy: "Ada Lovelace (ada)",
  purpose: "Laser alignment ahead of the quarterly imaging run.\nBring the calibration slides.",
  createdAt: "2026-08-30T07:14:00Z",
  updatedAt: "2026-08-31T14:40:00Z",
};

const DISPLAY_TIMEZONE = "Europe/London";

const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: DISPLAY_TIMEZONE }).format(
    new Date(value),
  );

/** Date alone, for the placements that cannot afford a full timestamp. */
const dateOnly = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeZone: DISPLAY_TIMEZONE }).format(new Date(value));

type Timestamps = { createdAt: string; updatedAt: string; edited: boolean };

const timestampsFor = (neverEdited: boolean): Timestamps => ({
  createdAt: EVENT.createdAt,
  // An unedited row carries updatedAt === createdAt, so every placement has to decide whether to
  // repeat it. Each one below suppresses it rather than showing the same instant twice.
  updatedAt: neverEdited ? EVENT.createdAt : EVENT.updatedAt,
  edited: !neverEdited,
});

// --- The four placements --------------------------------------------------------------------------

/** Control: the plan as written. Two more rows at the end of the definition list. */
function TimestampRows({ timestamps }: { timestamps: Timestamps }) {
  return (
    <>
      <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
        <dt className="font-medium">Created</dt>
        <dd className="min-w-0">
          <time dateTime={timestamps.createdAt}>{dateTime(timestamps.createdAt)}</time>
        </dd>
      </div>
      {timestamps.edited ? (
        <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
          <dt className="font-medium">Last updated</dt>
          <dd className="min-w-0">
            <time dateTime={timestamps.updatedAt}>{dateTime(timestamps.updatedAt)}</time>
          </dd>
        </div>
      ) : null}
    </>
  );
}

/** One muted sentence under a hairline: present, but out of the way. */
function TimestampByline({ timestamps }: { timestamps: Timestamps }) {
  return (
    <CardFooter className="border-t pt-4 text-sm text-muted-foreground">
      <p data-slot="timestamps" className="min-w-0">
        Created <time dateTime={timestamps.createdAt}>{dateTime(timestamps.createdAt)}</time>
        {timestamps.edited ? (
          <>
            {" · updated "}
            <time dateTime={timestamps.updatedAt}>{dateTime(timestamps.updatedAt)}</time>
          </>
        ) : null}
      </p>
    </CardFooter>
  );
}

/** Provenance beside the item identity, before the card. Dates only; the header is crowded. */
function TimestampHeaderLine({ timestamps }: { timestamps: Timestamps }) {
  return (
    <p data-slot="timestamps" className="text-sm text-muted-foreground">
      Booked <time dateTime={timestamps.createdAt}>{dateOnly(timestamps.createdAt)}</time>
      {timestamps.edited ? (
        <>
          {" · edited "}
          <time dateTime={timestamps.updatedAt}>{dateOnly(timestamps.updatedAt)}</time>
        </>
      ) : null}
    </p>
  );
}

/** Collapsed until asked for, and the natural place to grow a per-event audit trail later. */
function TimestampDisclosure({ timestamps }: { timestamps: Timestamps }) {
  return (
    <CardFooter className="border-t pt-4">
      <Collapsible className="min-w-0">
        <CollapsibleTrigger className="flex min-h-8 items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
          <HistoryIcon aria-hidden="true" className="size-4" />
          History
        </CollapsibleTrigger>
        <CollapsibleContent>
          <dl data-slot="timestamps" className="space-y-1 pt-3 text-sm">
            <div className="flex flex-wrap gap-x-2">
              <dt className="text-muted-foreground">Created</dt>
              <dd>
                <time dateTime={timestamps.createdAt}>{dateTime(timestamps.createdAt)}</time>
              </dd>
            </div>
            {timestamps.edited ? (
              <div className="flex flex-wrap gap-x-2">
                <dt className="text-muted-foreground">Last updated</dt>
                <dd>
                  <time dateTime={timestamps.updatedAt}>{dateTime(timestamps.updatedAt)}</time>
                </dd>
              </div>
            ) : null}
          </dl>
        </CollapsibleContent>
      </Collapsible>
    </CardFooter>
  );
}

// --- The page the placements compete inside ---------------------------------------------------------

function TimestampPlacementPrototype({ placement }: { placement: Placement }) {
  const [neverEdited, setNeverEdited] = React.useState(false);
  const [narrow, setNarrow] = React.useState(false);
  const timestamps = timestampsFor(neverEdited);

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
          >
            {placement === "header" ? <TimestampHeaderLine timestamps={timestamps} /> : null}
          </InventoryItem>
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
                {...(placement === "rows" ? { "data-slot": "timestamps" } : {})}
              >
                <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                  <dt className="font-medium">When</dt>
                  <dd className="min-w-0">
                    <time dateTime={EVENT.start}>{dateTime(EVENT.start)}</time>
                    {" – "}
                    <time dateTime={EVENT.end}>{dateTime(EVENT.end)}</time>
                  </dd>
                </div>
                <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                  <dt className="font-medium">Booked by</dt>
                  <dd className="min-w-0">
                    <UserBadge name={EVENT.bookedBy} />
                  </dd>
                </div>
                <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
                  <dt className="font-medium">Purpose</dt>
                  <dd className="min-w-0 whitespace-pre-line">{EVENT.purpose}</dd>
                </div>
                {placement === "rows" ? <TimestampRows timestamps={timestamps} /> : null}
              </dl>
            </div>
          </CardContent>
          {placement === "footer" ? <TimestampByline timestamps={timestamps} /> : null}
          {placement === "disclosure" ? <TimestampDisclosure timestamps={timestamps} /> : null}
        </Card>
      </div>
    </div>
  );
}

function PrototypePage({ placement }: { placement: Placement }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <TimestampPlacementPrototype placement={placement} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Timestamp Placement",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance ----------------------------------------------------------------------------------

/** Every placement has to survive both toggles; only where the timestamps live may differ. */
const sharedAcceptance = async (
  canvasElement: HTMLElement,
  step: Parameters<NonNullable<Story["play"]>>[0]["step"],
) => {
  const canvas = within(canvasElement.ownerDocument.body);
  const revealed = async () => {
    let region: HTMLElement | null = null;
    await waitFor(() => {
      region = canvasElement.ownerDocument.querySelector('[data-slot="timestamps"]');
      expect(region).not.toBeNull();
    });
    return region as unknown as HTMLElement;
  };

  await step("an unedited event does not repeat the same instant twice", async () => {
    expect(await revealed()).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
    const region = await revealed();
    expect(within(region).queryByText(/updated|edited/i)).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("checkbox", { name: /Never edited/ }));
  });

  await step("320 px keeps the timestamps readable without horizontal overflow", async () => {
    await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
    const region = await revealed();
    expect(region.scrollWidth).toBeLessThanOrEqual(region.clientWidth + 1);
  });
};

const rowsAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await sharedAcceptance(canvasElement, step);

  await step("the timestamps sit inside the same list as the booking's own facts", async () => {
    const list = canvas.getByText("Purpose").closest("dl") as HTMLElement;
    expect(within(list).getByText("Created")).toBeInTheDocument();
  });
};

const footerAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await sharedAcceptance(canvasElement, step);
  const byline = () => canvasElement.ownerDocument.querySelector('[data-slot="timestamps"]') as HTMLElement;

  await step("the byline reads as one sentence outside the definition list", async () => {
    const list = canvas.getByText("Purpose").closest("dl") as HTMLElement;
    expect(within(list).queryByText("Created")).not.toBeInTheDocument();
    expect(byline()).toHaveTextContent("Created 30 Aug 2026, 08:14 · updated 31 Aug 2026, 15:40");
  });
};

const headerAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await sharedAcceptance(canvasElement, step);
  const line = () => canvasElement.ownerDocument.querySelector('[data-slot="timestamps"]') as HTMLElement;

  await step("provenance sits with the item identity, above the card", async () => {
    const heading = canvas.getByRole("heading", { name: "Confocal microscope" });
    const header = heading.closest("section") as HTMLElement;
    expect(header.querySelector('[data-slot="timestamps"]')).not.toBeNull();
    // Dates only: the header already carries the global id and the status badges.
    expect(line()).toHaveTextContent("Booked 30 Aug 2026 · edited 31 Aug 2026");
  });
};

const disclosureAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);

  await step("the timestamps stay closed until asked for", async () => {
    expect(canvas.queryByText(/30 Aug 2026/)).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "History" }));
    expect(await canvas.findByText(/30 Aug 2026, 08:14/)).toBeInTheDocument();
  });

  await sharedAcceptance(canvasElement, step);
};

/** The plan as written: two more rows at the end of the definition list. */
export const DefinitionListRows: Story = {
  args: { placement: "rows" },
  play: import.meta.env.MODE === "test" ? rowsAcceptance : undefined,
};

/** One muted sentence under a hairline, outside the scanning path. */
export const CardFooterByline: Story = {
  args: { placement: "footer" },
  play: import.meta.env.MODE === "test" ? footerAcceptance : undefined,
};

/** Provenance beside the item identity, before the details card. */
export const HeaderSubtitle: Story = {
  args: { placement: "header" },
  play: import.meta.env.MODE === "test" ? headerAcceptance : undefined,
};

/** Collapsed behind a History toggle, with room for a later per-event audit trail. */
export const HistoryDisclosure: Story = {
  args: { placement: "disclosure" },
  play: import.meta.env.MODE === "test" ? disclosureAcceptance : undefined,
};
