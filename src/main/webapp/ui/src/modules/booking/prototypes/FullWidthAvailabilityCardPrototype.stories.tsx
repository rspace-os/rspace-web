// PROTOTYPE — a custom-rendered TableList field that needs the full card width.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { AvailabilityBar, type AvailabilityInterval } from "@/modules/booking/components/AvailabilityBar";
import {
  StandardTableListCardGrid,
  type StandardTableListCardItem,
} from "@/modules/common/table-list/prototypes/StandardTableListCardPrototype";
import { Button } from "@/modules/common/ui/button";

const now = new Date("2026-08-17T08:30:00.000Z");
const intervals: readonly AvailabilityInterval[] = [
  {
    kind: "booking",
    startsAt: new Date("2026-08-17T09:00:00.000Z"),
    endsAt: new Date("2026-08-17T11:30:00.000Z"),
  },
  {
    kind: "booking",
    startsAt: new Date("2026-08-17T14:00:00.000Z"),
    endsAt: new Date("2026-08-17T16:00:00.000Z"),
  },
];

const facilities: readonly StandardTableListCardItem[] = [
  {
    id: "FC1001",
    accessibleName: "Controlled-environment chamber",
    title: (
      <a className="font-heading font-medium text-link hover:underline" href="#FC1001">
        Controlled-environment chamber
      </a>
    ),
    fields: [
      { id: "facility", label: "Facility", value: "Plant Sciences, room 2.14" },
      { id: "owner", label: "Owner", value: "Growth Facilities Team" },
      {
        id: "availability",
        label: "Availability",
        fullWidth: true,
        value: (
          <AvailabilityBar
            intervals={intervals}
            periodStart={new Date("2026-08-17T00:00:00.000Z")}
            periodEnd={new Date("2026-08-18T00:00:00.000Z")}
            now={now}
            showCurrentAvailability
            timeZone="UTC"
            itemName="Controlled-environment chamber"
          />
        ),
      },
    ],
    actions: (
      <Button type="button" size="sm">
        Reserve
      </Button>
    ),
  },
];

function FullWidthAvailabilityCardPrototype() {
  return (
    <main className="min-h-screen bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-5xl space-y-5">
        <header className="space-y-1">
          <h1 className="font-heading text-2xl font-semibold">Shared facilities</h1>
          <p className="text-sm text-muted-foreground">
            Compact metadata and a custom-rendered field that uses the full card width.
          </p>
        </header>
        <StandardTableListCardGrid label="Shared facility cards" items={facilities} />
        <output className="block rounded-sm border border-dashed bg-muted/30 px-3 py-2 font-mono text-xs text-muted-foreground">
          Prototype state: collection=shared-facilities; view=card; fullWidthField=availability
        </output>
      </div>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/FullWidthAvailabilityCard",
  component: FullWidthAvailabilityCardPrototype,
  parameters: { layout: "fullscreen", viewport: { defaultViewport: "mobile1" } },
} satisfies Meta<typeof FullWidthAvailabilityCardPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;
export const StandardCard: Story = {};
