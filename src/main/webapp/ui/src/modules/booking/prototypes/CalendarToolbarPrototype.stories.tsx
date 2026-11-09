// PROTOTYPE ONLY: compare ways to streamline the calendar's display and filter buttons.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { PlusIcon } from "lucide-react";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Button } from "@/modules/common/ui/button";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import {
  CompactToolbar,
  CurrentToolbar,
  LayoutTabs,
  MockCalendarBody,
  TabsAndChipsToolbar,
  ViewMenuToolbar,
} from "./CalendarToolbarPrototypeViews.story";
import { useCalendarState } from "./calendarToolbarPrototypeState";

// ---------------------------------------------------------------------------------------------
// Frame and story wiring
// ---------------------------------------------------------------------------------------------

const VARIANTS = {
  Current: {
    summary: "Today's controls: two segmented groups, two quick-filter toggles, Filters and Reset on one wrapping row.",
    tradeOff: "Everything is one click away, but about a dozen controls wrap onto extra lines below ~1280px.",
  },
  "View menu": {
    summary:
      "Layout and period in one View menu; quick filters become switches inside one Filters popover; applied filters show as chips.",
    tradeOff: "Fewest buttons and never wraps, but changing layout or period costs an extra click.",
  },
  "Compact switchers": {
    summary:
      "Period stays a one-click group; layout shrinks to icons; quick filters fold into a summarising Show menu.",
    tradeOff: "Keeps the frequent controls direct; icon-only layout relies on tooltips to be learnable.",
  },
  "Tabs and chips": {
    summary:
      "Layout becomes tabs on the calendar with the period beside them; filters live in a chip row with Reset view.",
    tradeOff: "Separates viewing from filtering clearly, but uses one more row of vertical space.",
  },
} as const;

type Variant = keyof typeof VARIANTS;

function PrototypeCalendar({ variant, appliedFilters }: { variant: Variant; appliedFilters: number }) {
  const calendar = useCalendarState(appliedFilters);
  const body = <MockCalendarBody calendar={calendar} />;
  return (
    <section aria-label={variant} className="space-y-3">
      <div className="text-sm">
        <h2 className="font-semibold">{variant}</h2>
        <p className="text-muted-foreground">{VARIANTS[variant].summary}</p>
        <p className="text-muted-foreground">Trade-off: {VARIANTS[variant].tradeOff}</p>
      </div>
      <div className="rounded-sm border bg-background p-4 sm:p-6">
        <header className="flex flex-wrap items-center justify-between gap-3 pb-3">
          <h3 className="text-2xl font-semibold">Calendar</h3>
          {/* Placeholder for BookingCreationButtonGroup, which needs router context. */}
          <Button type="button">
            <PlusIcon aria-hidden="true" data-icon="inline-start" />
            New booking
          </Button>
        </header>
        {variant === "Current" ? <CurrentToolbar calendar={calendar} /> : null}
        {variant === "View menu" ? <ViewMenuToolbar calendar={calendar} /> : null}
        {variant === "Compact switchers" ? <CompactToolbar calendar={calendar} /> : null}
        {variant === "Tabs and chips" ? <TabsAndChipsToolbar calendar={calendar} /> : null}
        {variant === "Tabs and chips" ? <LayoutTabs calendar={calendar}>{body}</LayoutTabs> : body}
      </div>
    </section>
  );
}

const FRAME_WIDTHS = [1280, 1024, 768] as const;

function CalendarToolbarPrototype({
  variant,
  frameWidth,
  appliedFilters,
}: {
  variant: Variant | "All";
  frameWidth: (typeof FRAME_WIDTHS)[number];
  appliedFilters: 0 | 2;
}) {
  const variants = variant === "All" ? (Object.keys(VARIANTS) as Variant[]) : [variant];
  return (
    <TooltipProvider delay={250}>
      <main className="min-h-screen bg-muted/40 p-4 text-foreground">
        <div className="mx-auto space-y-10" style={{ maxWidth: frameWidth }}>
          {variants.map((name) => (
            <PrototypeCalendar key={name} variant={name} appliedFilters={appliedFilters} />
          ))}
        </div>
      </main>
    </TooltipProvider>
  );
}

const meta = {
  title: "Booking/Prototypes/Calendar toolbar",
  component: CalendarToolbarPrototype,
  parameters: { layout: "fullscreen" },
  args: { variant: "Current", frameWidth: 1280, appliedFilters: 0 },
  argTypes: {
    variant: { control: "select", options: [...Object.keys(VARIANTS), "All"] },
    frameWidth: { control: "select", options: FRAME_WIDTHS },
    appliedFilters: { control: "select", options: [0, 2] },
  },
  // Remount when Storybook args change so the in-page state starts from them.
  render: (args) => <CalendarToolbarPrototype key={JSON.stringify(args)} {...args} />,
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof CalendarToolbarPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Current: Story = {};
export const ViewMenu: Story = { args: { variant: "View menu" } };
export const CompactSwitchers: Story = { args: { variant: "Compact switchers" } };
export const TabsAndChips: Story = { args: { variant: "Tabs and chips" } };
export const AllAtLaptopWidth: Story = { args: { variant: "All", frameWidth: 1024, appliedFilters: 2 } };
export const AllAtTabletWidth: Story = { args: { variant: "All", frameWidth: 768, appliedFilters: 2 } };
