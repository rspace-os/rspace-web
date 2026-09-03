// PROTOTYPE ONLY (RPD-183). Expands the MetadataAside idea from BookingEventDetailsLayoutPrototype
// onto the bookable item (instrument) page: header, tab strip and bookings list are static stand-ins
// for BookableItemPage; only the aside is under study. One story per way of spending the aside:
//   FactsAside        direct port: location, timezone, created by, last updated. Rules stay in Details
//   RulesDigest       the Details tab's rules read-out moves into the aside; Details becomes edit-only
//   StatusFirst       live state first (available now, next booking, owner health), provenance below
//   CollapsibleGroups Item / Rules / People / Activity as collapsible groups, only the first open
//   PeopleAndActions  owners, effective role, primary actions; timestamps reduced to a footer line
// The shared ui/sidebar primitive is the app-shell nav rail (Provider, Trigger, Inset), not an
// in-page panel, so the aside is a plain grid column as on the event page.
// Schema note: BookingConfiguration exposes updatedAt and createdBy but no createdAt, so the aside
// shows "Created by" without a time; a created-at timestamp needs a backend field.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ChevronDownIcon, TriangleAlertIcon } from "lucide-react";
import * as React from "react";
import { expect, userEvent, within } from "storybook/test";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { Separator } from "@/modules/common/ui/separator";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

type Variant = "facts" | "rules" | "status" | "groups" | "people";

// --- Fixture -----------------------------------------------------------------------------------

const ITEM = {
  name: "Confocal microscope",
  globalId: "IN123",
  parentContainerName: "Imaging lab",
  parentContainerGlobalId: "IC456",
  timezone: "Europe/Berlin",
  enabled: true,
  openingStart: "08:00",
  openingEnd: "17:00",
  slotGranularityMinutes: 30,
  maxBookingDurationMinutes: 240,
  bufferBeforeMinutes: 15,
  bufferAfterMinutes: 0,
  allowDoubleBooking: false,
  createdBy: "Grace Hopper (grace)",
  updatedAt: "2026-08-10T10:00:00Z",
  owners: ["Grace Hopper (grace)", "Ada Lovelace (ada)"],
  effectiveRole: "Booker",
};

const NOW = new Date("2026-09-02T09:00:00Z");
const BOOKINGS = Array.from({ length: 12 }, (_, index) => ({
  id: index,
  start: new Date(NOW.getTime() + (index + 1) * 26 * 3_600_000).toISOString(),
  bookedBy: index % 3 === 0 ? "Ada Lovelace (ada)" : "Alan Turing (alan)",
}));

const dateTime = (value: string) =>
  new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: ITEM.timezone }).format(
    new Date(value),
  );
const minutes = (value: number) => (value === 0 ? "Unlimited" : `${value} min`);

const RULES: Array<[string, string]> = [
  ["Opening hours", `${ITEM.openingStart}–${ITEM.openingEnd}`],
  ["Slot", minutes(ITEM.slotGranularityMinutes)],
  ["Maximum duration", minutes(ITEM.maxBookingDurationMinutes)],
  ["Buffer before", `${ITEM.bufferBeforeMinutes} min`],
  ["Buffer after", `${ITEM.bufferAfterMinutes} min`],
  ["Double booking", ITEM.allowDoubleBooking ? "Yes" : "No"],
];

// --- Shared pieces ---------------------------------------------------------------------------------

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

function LocationRow() {
  return (
    <Row label="Location">
      <InventoryLocationLink name={ITEM.parentContainerName} globalId={ITEM.parentContainerGlobalId} />
    </Row>
  );
}

function ProvenanceRows() {
  return (
    <>
      <Row label="Created by">
        <UserBadge name={ITEM.createdBy} />
      </Row>
      <Row label="Last updated">
        <time dateTime={ITEM.updatedAt}>{dateTime(ITEM.updatedAt)}</time>
      </Row>
    </>
  );
}

function RulesRows() {
  return (
    <>
      {RULES.map(([label, value]) => (
        <Row key={label} label={label}>
          {value}
        </Row>
      ))}
    </>
  );
}

function AsideCard({
  title,
  children,
  footer,
}: {
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  const id = React.useId();
  return (
    <aside aria-labelledby={id} className="min-w-0">
      <Card size="sm">
        <CardHeader>
          <CardTitle id={id}>{title}</CardTitle>
        </CardHeader>
        <CardContent>{children}</CardContent>
        {footer ? <CardFooter className="border-t pt-3 text-xs text-muted-foreground">{footer}</CardFooter> : null}
      </Card>
    </aside>
  );
}

/** ponytail: static stand-in for SpotlightHeader + Tabs.List + BookingEventList. */
function PageStandIn({ rulesInAside }: { rulesInAside: boolean }) {
  return (
    <>
      <section className="flex flex-wrap items-center gap-4">
        <InventoryItem
          name={ITEM.name}
          nameAs="h1"
          globalId={ITEM.globalId}
          idPlacement="title"
          className="min-w-full flex-1 p-0 sm:min-w-0"
        >
          <span>{ITEM.timezone}</span>
        </InventoryItem>
        <div className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0">
          <Badge>Enabled</Badge>
          <Button size="sm">Book</Button>
        </div>
      </section>
      <div role="tablist" aria-label="Item sections" className="flex flex-wrap border-b text-sm font-medium">
        {["Bookings", rulesInAside ? "Edit rules" : "Details", "Audit", "Access"].map((tab, index) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={index === 0}
            className={cn(
              "-mb-px border-b-2 px-4 py-3",
              index === 0 ? "border-primary" : "border-transparent text-muted-foreground",
            )}
          >
            {tab}
          </button>
        ))}
      </div>
      <section role="tabpanel" aria-label="Bookings" className="space-y-4">
        <h2 className="text-lg font-semibold">Upcoming</h2>
        <ul className="divide-y rounded-sm border bg-card text-sm">
          {BOOKINGS.map((booking) => (
            <li key={booking.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
              <time dateTime={booking.start}>{dateTime(booking.start)}</time>
              <UserBadge name={booking.bookedBy} />
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}

// --- Aside variants ----------------------------------------------------------------------------------

function FactsAsideCard() {
  return (
    <AsideCard title="About this item">
      <dl data-slot="timestamps" className="space-y-3 text-sm">
        <LocationRow />
        <Row label="Times shown in">{ITEM.timezone}</Row>
        <ProvenanceRows />
      </dl>
    </AsideCard>
  );
}

function RulesDigestCard() {
  return (
    <AsideCard
      title="Booking rules"
      footer={
        <p data-slot="timestamps">
          Updated <time dateTime={ITEM.updatedAt}>{dateTime(ITEM.updatedAt)}</time> by {ITEM.createdBy}
        </p>
      }
    >
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        {RULES.map(([label, value]) => (
          <React.Fragment key={label}>
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="min-w-0 text-right font-medium">{value}</dd>
          </React.Fragment>
        ))}
      </dl>
    </AsideCard>
  );
}

function StatusFirstCard() {
  const next = BOOKINGS[0];
  return (
    <AsideCard title="Status">
      <div className="space-y-3 text-sm">
        <p className="flex items-center gap-2">
          <span aria-hidden="true" className="size-2 rounded-full bg-green-600" />
          <span className="font-medium">Available now</span>
        </p>
        <p className="text-muted-foreground">
          Next booking <time dateTime={next.start}>{dateTime(next.start)}</time>
        </p>
        <p
          role="status"
          className="flex items-start gap-2 rounded-sm border border-amber-300 bg-amber-50 p-2 text-amber-900"
        >
          <TriangleAlertIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          No active owner. Access changes need a sysadmin.
        </p>
        <Separator />
        <dl data-slot="timestamps" className="space-y-3">
          <LocationRow />
          <ProvenanceRows />
        </dl>
      </div>
    </AsideCard>
  );
}

function Group({ title, defaultOpen, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  return (
    <Collapsible defaultOpen={defaultOpen} className="group/collapsible">
      <CollapsibleTrigger className="flex w-full items-center justify-between py-2 text-left text-sm font-medium">
        {title}
        <ChevronDownIcon
          aria-hidden="true"
          className="size-4 transition-transform group-data-open/collapsible:rotate-180"
        />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <dl className="space-y-3 pb-3 text-sm">{children}</dl>
      </CollapsibleContent>
    </Collapsible>
  );
}

function CollapsibleGroupsCard() {
  return (
    <AsideCard title="About this item">
      <div data-slot="timestamps" className="divide-y">
        <Group title="Item" defaultOpen>
          <LocationRow />
          <Row label="Times shown in">{ITEM.timezone}</Row>
        </Group>
        <Group title="Rules">
          <RulesRows />
        </Group>
        <Group title="People">
          <Row label="Owners">
            <ul className="space-y-1">
              {ITEM.owners.map((owner) => (
                <li key={owner}>
                  <UserBadge name={owner} />
                </li>
              ))}
            </ul>
          </Row>
        </Group>
        <Group title="Activity">
          <ProvenanceRows />
        </Group>
      </div>
    </AsideCard>
  );
}

function PeopleAndActionsCard() {
  return (
    <AsideCard
      title="People"
      footer={
        <p data-slot="timestamps">
          Created by {ITEM.createdBy} · updated <time dateTime={ITEM.updatedAt}>{dateTime(ITEM.updatedAt)}</time>
        </p>
      }
    >
      <div className="space-y-4 text-sm">
        <dl className="space-y-3">
          <Row label="Owners">
            <ul className="space-y-1">
              {ITEM.owners.map((owner) => (
                <li key={owner}>
                  <UserBadge name={owner} />
                </li>
              ))}
            </ul>
          </Row>
          <Row label="Your role">
            <Badge variant="outline">{ITEM.effectiveRole}</Badge>
          </Row>
        </dl>
        <div className="flex flex-col gap-2">
          <Button size="sm">Book</Button>
          <Button size="sm" variant="outline">
            Subscribe to calendar
          </Button>
          <Button size="sm" variant="ghost">
            Edit rules
          </Button>
        </div>
      </div>
    </AsideCard>
  );
}

const ASIDES: Record<Variant, { card: React.ReactNode; first: boolean }> = {
  facts: { card: <FactsAsideCard />, first: false },
  rules: { card: <RulesDigestCard />, first: false },
  status: { card: <StatusFirstCard />, first: true },
  groups: { card: <CollapsibleGroupsCard />, first: false },
  people: { card: <PeopleAndActionsCard />, first: true },
};

// --- Harness ---------------------------------------------------------------------------------------

function AsidePrototype({ variant }: { variant: Variant }) {
  const [narrow, setNarrow] = React.useState(false);
  const [sticky, setSticky] = React.useState(true);
  const { card, first } = ASIDES[variant];
  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <div className="flex flex-wrap gap-4">
        <span className="flex items-center gap-2">
          <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
          <Label htmlFor="narrow" className="text-xs">
            Constrain page to 320 px
          </Label>
        </span>
        <span className="flex items-center gap-2">
          <Checkbox id="sticky" checked={sticky} onCheckedChange={(checked) => setSticky(checked === true)} />
          <Label htmlFor="sticky" className="text-xs">
            Sticky aside
          </Label>
        </span>
      </div>
      {/* Container queries, not viewport ones: the toggle narrows this box, not the window. */}
      <div data-slot="page" className={cn("@container", narrow && "max-w-[320px]")}>
        <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_16rem]">
          <div className="min-w-0 space-y-6">
            <PageStandIn rulesInAside={variant === "rules"} />
          </div>
          {/* State and actions stack above the tabs when narrow; provenance stacks below. */}
          <div
            data-slot="aside"
            className={cn(
              "min-w-0 @2xl:order-none @2xl:self-start",
              first && "order-first",
              sticky && "@2xl:sticky @2xl:top-4",
            )}
          >
            {card}
          </div>
        </div>
      </div>
    </div>
  );
}

function PrototypePage({ variant }: { variant: Variant }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <AsidePrototype variant={variant} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Bookable Item Details Aside",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance ----------------------------------------------------------------------------------

const narrowAndCheck = async (canvasElement: HTMLElement, asideFirst: boolean) => {
  const canvas = within(canvasElement.ownerDocument.body);
  await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
  const page = canvasElement.ownerDocument.querySelector('[data-slot="page"]') as HTMLElement;
  const limit = page.getBoundingClientRect().right + 1;
  for (const node of page.querySelectorAll("*")) {
    expect(node.getBoundingClientRect().right).toBeLessThanOrEqual(limit);
  }
  const aside = page.querySelector('[data-slot="aside"]') as HTMLElement;
  const tabs = canvas.getByRole("tablist");
  const asideAboveTabs = aside.getBoundingClientRect().top < tabs.getBoundingClientRect().top;
  expect(asideAboveTabs).toBe(asideFirst);
};

const acceptance =
  (name: string, unique: (canvas: ReturnType<typeof within>) => void | Promise<void>, first: boolean): Story["play"] =>
  async ({ canvasElement, step }) => {
    const canvas = within(canvasElement.ownerDocument.body);
    await step(`the aside is a labelled complementary region named "${name}"`, async () => {
      await canvas.findByRole("complementary", { name });
      await unique(canvas);
    });
    await step(
      first
        ? "320 px puts the aside above the tabs without overflow"
        : "320 px puts the aside below the list without overflow",
      () => narrowAndCheck(canvasElement, first),
    );
  };

/** Location, timezone, created by, last updated. Rules stay in the Details tab. Stacks below when narrow. */
export const FactsAside: Story = {
  args: { variant: "facts" },
  play:
    import.meta.env.MODE === "test"
      ? acceptance(
          "About this item",
          (canvas) => {
            const aside = canvas.getByRole("complementary");
            expect(within(aside).getByText("Created by")).toBeInTheDocument();
            expect(within(aside).getByText("Last updated")).toBeInTheDocument();
            expect(within(aside).queryByText("Opening hours")).not.toBeInTheDocument();
          },
          false,
        )
      : undefined,
};

/** The rules read-out moves into the aside; the Details tab becomes "Edit rules". Stacks below when narrow. */
export const RulesDigest: Story = {
  args: { variant: "rules" },
  play:
    import.meta.env.MODE === "test"
      ? acceptance(
          "Booking rules",
          (canvas) => {
            const aside = canvas.getByRole("complementary");
            expect(within(aside).getByText("Opening hours")).toBeInTheDocument();
            expect(canvas.getByRole("tab", { name: "Edit rules" })).toBeInTheDocument();
            expect(canvas.queryByRole("tab", { name: "Details" })).not.toBeInTheDocument();
          },
          false,
        )
      : undefined,
};

/** Live state first: available now, next booking, owner-health warning. Provenance below. Stacks above when narrow. */
export const StatusFirst: Story = {
  args: { variant: "status" },
  play:
    import.meta.env.MODE === "test"
      ? acceptance(
          "Status",
          (canvas) => {
            expect(canvas.getByText("Available now")).toBeInTheDocument();
            expect(canvas.getByRole("status")).toHaveTextContent(/No active owner/);
          },
          true,
        )
      : undefined,
};

/** Item / Rules / People / Activity as collapsible groups, only Item open by default. Stacks below when narrow. */
export const CollapsibleGroups: Story = {
  args: { variant: "groups" },
  play:
    import.meta.env.MODE === "test"
      ? acceptance(
          "About this item",
          async (canvas) => {
            const aside = canvas.getByRole("complementary");
            const expanded = within(aside)
              .getAllByRole("button")
              .filter((button) => button.getAttribute("aria-expanded") === "true");
            expect(expanded.map((button) => button.textContent)).toEqual(["Item"]);
            expect(within(aside).queryByText("Last updated")).not.toBeInTheDocument();
            await userEvent.click(within(aside).getByRole("button", { name: "Activity" }));
            expect(await within(aside).findByText("Last updated")).toBeVisible();
          },
          false,
        )
      : undefined,
};

/** Owners, your role and the primary actions; timestamps as one footer line. Stacks above when narrow. */
export const PeopleAndActions: Story = {
  args: { variant: "people" },
  play:
    import.meta.env.MODE === "test"
      ? acceptance(
          "People",
          (canvas) => {
            const aside = canvas.getByRole("complementary");
            expect(
              within(aside)
                .getAllByRole("button")
                .map((button) => button.textContent),
            ).toEqual(["Book", "Subscribe to calendar", "Edit rules"]);
            expect(within(aside).getByText(/^Created by/)).toHaveTextContent(/updated 10 Aug 2026, 12:00/);
          },
          true,
        )
      : undefined,
};
