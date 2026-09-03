// PROTOTYPE ONLY (booking-event-details-page plan, .claude/booking-event-details-page-plan.md).
// BookingEventDetailsPrototype builds the plan's page as written: one card, one definition list.
// This file tries three other shapes for the same facts, one per story:
//   MetadataAside   two columns when the page is wider than 42rem: the booking's own facts on the left, an "About"
//                   aside on the right holding provenance (created, updated, timezone, item)
//   SummaryTiles    when / where / who as three tiles under the heading, purpose below, and the
//                   timestamps reduced to one footer line
//   SidePanelSheet  the details as a Sheet over the calendar instead of a page. A departure from
//                   the plan's dedicated-page model, kept here so the trade-off is visible: no
//                   URL of its own, no edit route, but the calendar context never leaves the screen
// Layout study only: no router, no query, no network. Times are UTC instants rendered in a fixed
// display timezone so the copy is deterministic.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarClockIcon, MapPinIcon, UserRoundIcon } from "lucide-react";
import * as React from "react";
import { expect, userEvent, within } from "storybook/test";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/modules/common/ui/sheet";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

type Layout = "aside" | "tiles" | "sheet";

// --- Fixture -----------------------------------------------------------------------------------

const EVENT = {
  target: {
    name: "Confocal microscope",
    globalId: "IN123",
    parentContainerName: "Imaging suite, bench 2",
    parentContainerGlobalId: "IC55",
  },
  start: "2026-09-12T08:00:00Z",
  end: "2026-09-12T10:00:00Z",
  timezone: "Europe/London",
  bookedBy: "Ada Lovelace (ada)",
  purpose: "Laser alignment ahead of the quarterly imaging run.\nBring the calibration slides.",
  createdAt: "2026-08-30T07:14:00Z",
  updatedAt: "2026-08-31T14:40:00Z",
};

const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: EVENT.timezone }).format(
    new Date(value),
  );
const timeOnly = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { timeStyle: "short", timeZone: EVENT.timezone }).format(new Date(value));

// --- Shared pieces ---------------------------------------------------------------------------------

function PageHeader({ children }: { children?: React.ReactNode }) {
  return (
    <section className="flex flex-wrap items-center gap-4">
      <InventoryItem
        name={EVENT.target.name}
        nameAs="h1"
        globalId={EVENT.target.globalId}
        idPlacement="title"
        className="min-w-full flex-1 p-0 sm:min-w-0"
      >
        {children}
      </InventoryItem>
      <div className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0">
        <Badge variant="outline">Booking</Badge>
        <Badge>Confirmed</Badge>
        <Button variant="outline" size="sm">
          Edit
        </Button>
      </div>
    </section>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME}>
      <dt className="font-medium">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

function WhenRow() {
  return (
    <Row label="When">
      <time dateTime={EVENT.start}>{dateTime(EVENT.start)}</time>
      {" – "}
      <time dateTime={EVENT.end}>{dateTime(EVENT.end)}</time>
    </Row>
  );
}

// --- Layout 1: metadata aside ----------------------------------------------------------------------

function MetadataAsideLayout() {
  return (
    <div className="space-y-6">
      <PageHeader />
      <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_16rem]">
        <Card>
          <CardHeader>
            <CardTitle>Booking details</CardTitle>
          </CardHeader>
          <CardContent>
            <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
              <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
                <WhenRow />
                <Row label="Booked by">
                  <UserBadge name={EVENT.bookedBy} />
                </Row>
                <Row label="Purpose">
                  <span className="whitespace-pre-line">{EVENT.purpose}</span>
                </Row>
              </dl>
            </div>
          </CardContent>
        </Card>
        <aside aria-labelledby="about-heading" className="min-w-0">
          <Card size="sm">
            <CardHeader>
              <CardTitle id="about-heading">About this booking</CardTitle>
            </CardHeader>
            <CardContent>
              <dl data-slot="timestamps" className="space-y-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">Item</dt>
                  <dd className="min-w-0">
                    <InventoryItem name={EVENT.target.name} globalId={EVENT.target.globalId} size="xs" href="#">
                      <InventoryLocationLink
                        name={EVENT.target.parentContainerName}
                        globalId={EVENT.target.parentContainerGlobalId}
                      />
                    </InventoryItem>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Times shown in</dt>
                  <dd>{EVENT.timezone}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Created</dt>
                  <dd>
                    <time dateTime={EVENT.createdAt}>{dateTime(EVENT.createdAt)}</time>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Last updated</dt>
                  <dd>
                    <time dateTime={EVENT.updatedAt}>{dateTime(EVENT.updatedAt)}</time>
                  </dd>
                </div>
              </dl>
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  );
}

// --- Layout 2: summary tiles -------------------------------------------------------------------------

function Tile({ icon: Icon, label, children }: { icon: typeof MapPinIcon; label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 items-start gap-3 rounded-sm border bg-card p-3">
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
      <dl className="min-w-0">
        <dt className="text-xs text-muted-foreground">{label}</dt>
        <dd className="min-w-0 text-sm font-medium">{children}</dd>
      </dl>
    </div>
  );
}

function SummaryTilesLayout() {
  return (
    <div className="space-y-6">
      <PageHeader />
      <div data-slot="tiles" className="grid gap-3 @2xl:grid-cols-3">
        <Tile icon={CalendarClockIcon} label="When">
          <time dateTime={EVENT.start}>{dateTime(EVENT.start)}</time>
          {" – "}
          <time dateTime={EVENT.end}>{timeOnly(EVENT.end)}</time>
        </Tile>
        <Tile icon={MapPinIcon} label="Where">
          {EVENT.target.parentContainerName}
        </Tile>
        <Tile icon={UserRoundIcon} label="Who">
          <UserBadge name={EVENT.bookedBy} />
        </Tile>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Purpose</CardTitle>
        </CardHeader>
        <CardContent className="whitespace-pre-line text-sm">{EVENT.purpose}</CardContent>
        <CardFooter className="border-t pt-4 text-sm text-muted-foreground">
          <p data-slot="timestamps" className="min-w-0">
            Created <time dateTime={EVENT.createdAt}>{dateTime(EVENT.createdAt)}</time>
            {" · updated "}
            <time dateTime={EVENT.updatedAt}>{dateTime(EVENT.updatedAt)}</time>
          </p>
        </CardFooter>
      </Card>
    </div>
  );
}

// --- Layout 3: side panel over the calendar --------------------------------------------------------

/** ponytail: the calendar is a static stand-in; only the panel is under study. */
function CalendarStandIn({ onOpen }: { onOpen: () => void }) {
  return (
    <div className="space-y-3">
      <h1 className="text-2xl font-semibold">Calendar</h1>
      <div className="grid grid-cols-[3rem_1fr] gap-x-2 rounded-sm border bg-card p-3 text-sm">
        {["08:00", "09:00", "10:00", "11:00"].map((hour, index) => (
          <React.Fragment key={hour}>
            <span className="text-muted-foreground">{hour}</span>
            <div className="min-h-10 border-t">
              {index === 0 ? (
                <button
                  type="button"
                  onClick={onOpen}
                  className="mt-1 w-full rounded-sm bg-primary/10 px-2 py-1 text-left text-primary hover:bg-primary/20"
                >
                  {EVENT.target.name} · Ada
                </button>
              ) : null}
            </div>
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

function SidePanelSheetLayout() {
  const [open, setOpen] = React.useState(false);
  return (
    <>
      <CalendarStandIn onOpen={() => setOpen(true)} />
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent className="flex w-full flex-col gap-0 sm:max-w-md">
          <SheetHeader className="border-b">
            <SheetTitle>{EVENT.target.name}</SheetTitle>
            <SheetDescription className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{EVENT.target.globalId}</Badge>
              <Badge>Confirmed</Badge>
            </SheetDescription>
          </SheetHeader>
          <div className="min-h-0 flex-1 overflow-y-auto p-4">
            <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
              <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4 text-sm`}>
                <WhenRow />
                <Row label="Booked by">
                  <UserBadge name={EVENT.bookedBy} />
                </Row>
                <Row label="Purpose">
                  <span className="whitespace-pre-line">{EVENT.purpose}</span>
                </Row>
              </dl>
            </div>
          </div>
          <SheetFooter className="flex-col items-stretch gap-3 border-t">
            <p data-slot="timestamps" className="text-xs text-muted-foreground">
              Created <time dateTime={EVENT.createdAt}>{dateTime(EVENT.createdAt)}</time>
              {" · updated "}
              <time dateTime={EVENT.updatedAt}>{dateTime(EVENT.updatedAt)}</time>
            </p>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" className="flex-1">
                Edit
              </Button>
              <Button variant="destructive" size="sm" className="flex-1">
                Cancel booking
              </Button>
            </div>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </>
  );
}

// --- Harness ---------------------------------------------------------------------------------------

function LayoutPrototype({ layout }: { layout: Layout }) {
  const [narrow, setNarrow] = React.useState(false);
  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <span className="flex items-center gap-2">
        <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
        <Label htmlFor="narrow" className="text-xs">
          Constrain page to 320 px
        </Label>
      </span>
      {/* Container queries, not viewport ones: the toggle narrows this box, not the window. */}
      <div data-slot="page" className={cn("@container", narrow && "max-w-[320px]")}>
        {layout === "aside" ? <MetadataAsideLayout /> : null}
        {layout === "tiles" ? <SummaryTilesLayout /> : null}
        {layout === "sheet" ? <SidePanelSheetLayout /> : null}
      </div>
    </div>
  );
}

function PrototypePage({ layout }: { layout: Layout }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <LayoutPrototype layout={layout} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Event Details Layouts",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance ----------------------------------------------------------------------------------

const noOverflowAt320 = async (canvasElement: HTMLElement) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
  const page = canvasElement.ownerDocument.querySelector('[data-slot="page"]') as HTMLElement;
  // Measure the descendants themselves: a grid may squeeze into the box or spill past it, and only
  // the second is a failure.
  const limit = page.getBoundingClientRect().right + 1;
  for (const node of page.querySelectorAll("*")) {
    expect(node.getBoundingClientRect().right).toBeLessThanOrEqual(limit);
  }
};

const asideAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await step("provenance lives in a labelled aside, not in the details list", async () => {
    const aside = await canvas.findByRole("complementary", { name: "About this booking" });
    expect(within(aside).getByText("Created")).toBeInTheDocument();
    expect(within(aside).getByText("Last updated")).toBeInTheDocument();
    const list = canvas.getByText("Purpose").closest("dl") as HTMLElement;
    expect(within(list).queryByText("Created")).not.toBeInTheDocument();
  });
  await step("320 px stacks the aside under the card without overflow", () => noOverflowAt320(canvasElement));
};

const tilesAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await step("when, where and who read as three tiles under the heading", async () => {
    await canvas.findByRole("heading", { name: "Confocal microscope" });
    const tiles = canvasElement.ownerDocument.querySelector('[data-slot="tiles"]') as HTMLElement;
    expect(
      within(tiles)
        .getAllByRole("term")
        .map((term) => term.textContent),
    ).toEqual(["When", "Where", "Who"]);
    // CardTitle renders a div, so the purpose card is found by its text.
    expect(canvas.getByText("Purpose")).toBeInTheDocument();
  });
  await step("320 px stacks the tiles without overflow", () => noOverflowAt320(canvasElement));
};

const sheetAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await step("the event opens a panel while the calendar stays behind it", async () => {
    const calendar = await canvas.findByRole("heading", { name: "Calendar" });
    await userEvent.click(canvas.getByRole("button", { name: /Confocal microscope · Ada/ }));
    const dialog = await canvas.findByRole("dialog", { name: "Confocal microscope" });
    expect(within(dialog).getByText("Purpose")).toBeInTheDocument();
    expect(within(dialog).getByText(/^Created/)).toHaveTextContent(
      "Created 30 Aug 2026, 08:14 · updated 31 Aug 2026, 15:40",
    );
    // The modal hides the page from assistive tech, but the calendar is still on screen behind it.
    expect(calendar).toBeVisible();
  });
};

/** Booking facts left, "About this booking" aside right; stacks when the page is narrower than 42rem. */
export const MetadataAside: Story = {
  args: { layout: "aside" },
  play: import.meta.env.MODE === "test" ? asideAcceptance : undefined,
};

/** When / where / who tiles, purpose card, timestamps as one footer line. */
export const SummaryTiles: Story = {
  args: { layout: "tiles" },
  play: import.meta.env.MODE === "test" ? tilesAcceptance : undefined,
};

/** The details as a Sheet over the calendar rather than a page of their own. */
export const SidePanelSheet: Story = {
  args: { layout: "sheet" },
  play: import.meta.env.MODE === "test" ? sheetAcceptance : undefined,
};
