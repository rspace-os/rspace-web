// PROTOTYPE ONLY: compare ways to streamline the calendar's display and filter buttons.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy. */
import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import { Tabs as TabsPrimitive } from "@base-ui/react/tabs";
import {
  CalendarCheck2Icon,
  CalendarRangeIcon,
  CheckIcon,
  ChevronDownIcon,
  ListFilterIcon,
  ListIcon,
  type LucideIcon,
  PackageCheckIcon,
  PlusIcon,
  RotateCcwIcon,
  Rows3Icon,
  SearchIcon,
  XIcon,
} from "lucide-react";
import { type CSSProperties, type ReactNode, useId, useState } from "react";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import {
  type CalendarLayout,
  type CalendarView,
  calendarLayouts,
  calendarViews,
  shiftDate,
} from "@/modules/booking/pages/calendar/calendarLayoutUtils";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Input } from "@/modules/common/ui/input";
import { Menu, MenuContent, MenuSeparator, MenuTrigger } from "@/modules/common/ui/menu";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { Switch } from "@/modules/common/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";

import { ITEMS, MOCK_FILTERS, type MockEvent } from "./calendarToolbarPrototypeFixtures";
import {
  type CalendarState,
  dayLabel,
  LAYOUT_LABELS,
  MINE_LABEL,
  MY_ITEMS_LABEL,
  PERIOD_LABELS,
  showSummary,
  TIME_ZONE,
  viewSummary,
  visibleEvents,
} from "./calendarToolbarPrototypeState";

const LAYOUT_ICONS: Record<CalendarLayout, LucideIcon> = {
  "time-grid": CalendarRangeIcon,
  resources: Rows3Icon,
  agenda: ListIcon,
};
const MONTH_REASON = "Month isn't available in Resources. Use Time grid or Agenda for a month overview.";

// Copied from common/ui/menu.tsx, which does not export it.
const menuItemClassName =
  "flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none data-highlighted:bg-muted data-disabled:pointer-events-none data-disabled:opacity-50";
const menuGroupLabelClassName = "px-2 pt-1.5 pb-1 text-xs font-medium text-muted-foreground";
const countBadgeClassName = "ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background";

const isLayout = (value: unknown): value is CalendarLayout => (calendarLayouts as readonly unknown[]).includes(value);
const isPeriod = (value: unknown): value is CalendarView => (calendarViews as readonly unknown[]).includes(value);

function DateNavigation({ calendar }: { calendar: CalendarState }) {
  const period = PERIOD_LABELS[calendar.period].toLocaleLowerCase();
  return (
    <BookingDateControls
      date={calendar.date}
      today={calendar.today}
      timeZone={TIME_ZONE}
      controlsLabel="Calendar date controls"
      navigationLabel="Calendar period navigation"
      previousLabel={`Previous ${period}`}
      todayLabel="Today"
      nextLabel={`Next ${period}`}
      jumpToDateLabel="Jump to date"
      onPrevious={() => calendar.setDate(shiftDate(calendar.date, calendar.period, -1))}
      onNext={() => calendar.setDate(shiftDate(calendar.date, calendar.period, 1))}
      onDateChange={calendar.setDate}
    />
  );
}

/** Mirrors the TableList search field, without the debounce. */
function SearchField({ calendar }: { calendar: CalendarState }) {
  return (
    <div className="relative min-w-56 flex-1">
      <SearchIcon
        aria-hidden="true"
        className="absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        aria-label="Search booking events"
        placeholder="Search booking events"
        className="h-8 bg-background pr-9 pl-9"
        value={calendar.search}
        onChange={(event) => calendar.setSearch(event.target.value)}
      />
    </div>
  );
}

function ResetButton({ calendar }: { calendar: CalendarState }) {
  if (!calendar.hasChanges) return null;
  return (
    <Tooltip>
      <TooltipTrigger
        render={<Button aria-label="Reset to defaults" size="icon" variant="ghost" onClick={calendar.reset} />}
      >
        <RotateCcwIcon aria-hidden="true" />
      </TooltipTrigger>
      <TooltipContent side="bottom" className="rounded-sm">
        Reset to defaults
      </TooltipContent>
    </Tooltip>
  );
}

function QuickToggle({
  pressed,
  icon: Icon,
  label,
  onPressedChange,
}: {
  pressed: boolean;
  icon: LucideIcon;
  label: string;
  onPressedChange: (pressed: boolean) => void;
}) {
  return (
    <Button
      type="button"
      aria-pressed={pressed}
      variant={pressed ? "secondary" : "outline"}
      onClick={() => onPressedChange(!pressed)}
    >
      <Icon aria-hidden="true" />
      {label}
    </Button>
  );
}

/** Stands in for the existing TableList filter panel, which none of the variants change. */
function MockFilterPanel({ calendar, onClose }: { calendar: CalendarState; onClose: () => void }) {
  return (
    <section
      aria-label="Filters"
      className="mt-2 flex flex-wrap items-center gap-2 rounded-sm border border-dashed p-3 text-sm"
    >
      <span className="text-muted-foreground">Existing filter builder (unchanged):</span>
      {calendar.filters.length === 0 ? <span>No filters</span> : null}
      {calendar.filters.map((filter) => (
        <FilterChip key={filter.id} label={filter.label} onRemove={() => calendar.removeFilter(filter.id)} />
      ))}
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={calendar.filters.length === MOCK_FILTERS.length}
        onClick={calendar.addFilter}
      >
        <PlusIcon aria-hidden="true" data-icon="inline-start" />
        Add mock filter
      </Button>
      <Button type="button" size="sm" variant="ghost" className="ml-auto" onClick={onClose}>
        Close
      </Button>
    </section>
  );
}

/** Styled like the calendar's route target chip. */
function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex max-w-full min-w-0 items-center gap-1 rounded-sm border bg-secondary py-0.5 pr-0.5 pl-2 text-xs font-medium text-secondary-foreground">
      <span className="min-w-0 truncate">{label}</span>
      <Button type="button" variant="ghost" size="icon-xs" aria-label={`Remove ${label}`} onClick={onRemove}>
        <XIcon aria-hidden="true" />
      </Button>
    </span>
  );
}

/** A segmented group; icon-only options take their name from a tooltip, and a disabled option explains itself. */
function Segmented<Option extends string>({
  legend,
  options,
  value,
  labels,
  icons,
  iconOnly = false,
  disabledReason,
  onChange,
}: {
  legend: string;
  options: readonly Option[];
  value: Option;
  labels: Record<Option, string>;
  icons?: Record<Option, LucideIcon>;
  iconOnly?: boolean;
  disabledReason?: (option: Option) => string | undefined;
  onChange: (option: Option) => void;
}) {
  const idPrefix = useId();
  return (
    <ButtonGroup aria-label={legend}>
      {options.map((option, index) => {
        const reason = disabledReason?.(option);
        const Icon: LucideIcon | undefined = icons?.[option];
        const tooltip = reason ?? (iconOnly ? labels[option] : undefined);
        const reasonId = `${idPrefix}-${option}`;
        const button = (
          <Button
            key={option}
            type="button"
            size={iconOnly ? "icon" : "default"}
            variant={value === option ? "secondary" : "outline"}
            aria-pressed={value === option}
            aria-label={iconOnly ? labels[option] : undefined}
            aria-describedby={reason ? reasonId : undefined}
            disabled={reason !== undefined}
            className={cn(
              tooltip && index > 0 && "rounded-l-none border-l-0",
              tooltip && index < options.length - 1 && "rounded-r-none",
            )}
            onClick={() => onChange(option)}
          >
            {Icon ? <Icon aria-hidden="true" /> : null}
            {iconOnly ? null : labels[option]}
          </Button>
        );
        if (!tooltip) return button;
        return (
          <Tooltip key={option}>
            <TooltipTrigger render={<span className="inline-flex" />}>
              {button}
              {reason ? (
                <span id={reasonId} className="sr-only">
                  {reason}
                </span>
              ) : null}
            </TooltipTrigger>
            <TooltipContent side="bottom" className="rounded-sm">
              {tooltip}
            </TooltipContent>
          </Tooltip>
        );
      })}
    </ButtonGroup>
  );
}

const monthReason = (calendar: CalendarState) => (option: CalendarView) =>
  calendar.layout === "resources" && option === "month" ? MONTH_REASON : undefined;

function RadioMenuItem({ value, children, disabled }: { value: string; children: ReactNode; disabled?: boolean }) {
  return (
    <MenuPrimitive.RadioItem value={value} disabled={disabled} className={menuItemClassName}>
      <span className="flex size-4 items-center justify-center">
        <MenuPrimitive.RadioItemIndicator>
          <CheckIcon aria-hidden="true" className="size-4" />
        </MenuPrimitive.RadioItemIndicator>
      </span>
      {children}
    </MenuPrimitive.RadioItem>
  );
}

function CheckboxMenuItem({
  checked,
  onCheckedChange,
  children,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  children: ReactNode;
}) {
  return (
    <MenuPrimitive.CheckboxItem checked={checked} onCheckedChange={onCheckedChange} className={menuItemClassName}>
      <span className="flex size-4 items-center justify-center">
        <MenuPrimitive.CheckboxItemIndicator>
          <CheckIcon aria-hidden="true" className="size-4" />
        </MenuPrimitive.CheckboxItemIndicator>
      </span>
      {children}
    </MenuPrimitive.CheckboxItem>
  );
}

function SwitchRow({
  label,
  description,
  checked,
  onCheckedChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    // biome-ignore lint/a11y/noLabelWithoutControl: Base UI's Switch renders its own input inside the label.
    <label className="flex items-start justify-between gap-3">
      <span>
        <span className="block font-medium">{label}</span>
        <span className="block text-xs text-muted-foreground">{description}</span>
      </span>
      <Switch checked={checked} onCheckedChange={(next) => onCheckedChange(next)} />
    </label>
  );
}

// ---------------------------------------------------------------------------------------------
// Current: the segmented controls production used before the View menu shipped.
// ---------------------------------------------------------------------------------------------

export function CurrentToolbar({ calendar }: { calendar: CalendarState }) {
  const [panelOpen, setPanelOpen] = useState(false);
  return (
    <>
      <div role="toolbar" aria-label="Booking events" className="flex flex-col flex-wrap gap-2 border-b py-2">
        <SearchField calendar={calendar} />
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <DateNavigation calendar={calendar} />
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Segmented
              legend="Layout"
              options={calendarLayouts}
              value={calendar.layout}
              labels={LAYOUT_LABELS}
              onChange={calendar.setLayout}
            />
            <Segmented
              legend="Period"
              options={calendarViews}
              value={calendar.period}
              labels={PERIOD_LABELS}
              disabledReason={monthReason(calendar)}
              onChange={calendar.setPeriod}
            />
          </div>
          <fieldset className="flex min-w-0 flex-wrap items-center gap-2">
            <legend className="sr-only">Booking event quick filters</legend>
            <QuickToggle
              pressed={calendar.mine}
              icon={CalendarCheck2Icon}
              label={MINE_LABEL}
              onPressedChange={calendar.setMine}
            />
            <QuickToggle
              pressed={calendar.myItems}
              icon={PackageCheckIcon}
              label={MY_ITEMS_LABEL}
              onPressedChange={calendar.setMyItems}
            />
          </fieldset>
          <Button
            type="button"
            aria-expanded={panelOpen}
            variant={panelOpen || calendar.filters.length > 0 ? "secondary" : "outline"}
            onClick={() => setPanelOpen(!panelOpen)}
          >
            <ListFilterIcon aria-hidden="true" data-icon="inline-start" />
            Filters
            {calendar.filters.length > 0 ? (
              <span className={countBadgeClassName}>{calendar.filters.length}</span>
            ) : null}
          </Button>
          <ResetButton calendar={calendar} />
        </div>
      </div>
      {panelOpen ? <MockFilterPanel calendar={calendar} onClose={() => setPanelOpen(false)} /> : null}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// A. View menu: layout and period in one menu; quick and advanced filters behind one Filters popover.
// ---------------------------------------------------------------------------------------------

export function ViewMenuToolbar({ calendar }: { calendar: CalendarState }) {
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const activeCount = Number(calendar.mine) + Number(calendar.myItems) + calendar.filters.length;
  const LayoutIcon = LAYOUT_ICONS[calendar.layout];
  const reason = monthReason(calendar);
  return (
    <>
      <div role="toolbar" aria-label="Booking events" className="flex flex-col gap-2 border-b py-2">
        <SearchField calendar={calendar} />
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <DateNavigation calendar={calendar} />
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Menu>
              <MenuTrigger
                render={<Button type="button" variant="outline" aria-label={`View: ${viewSummary(calendar)}`} />}
              >
                <LayoutIcon aria-hidden="true" data-icon="inline-start" />
                {viewSummary(calendar)}
                <ChevronDownIcon aria-hidden="true" data-icon="inline-end" />
              </MenuTrigger>
              <MenuContent className="w-64">
                <MenuPrimitive.RadioGroup
                  value={calendar.layout}
                  onValueChange={(value) => isLayout(value) && calendar.setLayout(value)}
                >
                  <MenuPrimitive.GroupLabel className={menuGroupLabelClassName}>Layout</MenuPrimitive.GroupLabel>
                  {calendarLayouts.map((option) => {
                    const Icon = LAYOUT_ICONS[option];
                    return (
                      <RadioMenuItem key={option} value={option}>
                        <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
                        {LAYOUT_LABELS[option]}
                      </RadioMenuItem>
                    );
                  })}
                </MenuPrimitive.RadioGroup>
                <MenuSeparator />
                <MenuPrimitive.RadioGroup
                  value={calendar.period}
                  onValueChange={(value) => isPeriod(value) && calendar.setPeriod(value)}
                >
                  <MenuPrimitive.GroupLabel className={menuGroupLabelClassName}>Period</MenuPrimitive.GroupLabel>
                  {calendarViews.map((option) => (
                    <RadioMenuItem key={option} value={option} disabled={reason(option) !== undefined}>
                      <span>
                        <span className="block">{PERIOD_LABELS[option]}</span>
                        {reason(option) ? (
                          <span className="block text-xs text-muted-foreground">Not available in Resources</span>
                        ) : null}
                      </span>
                    </RadioMenuItem>
                  ))}
                </MenuPrimitive.RadioGroup>
              </MenuContent>
            </Menu>
            <Popover open={filtersOpen} onOpenChange={setFiltersOpen}>
              <PopoverTrigger render={<Button type="button" variant={activeCount > 0 ? "secondary" : "outline"} />}>
                <ListFilterIcon aria-hidden="true" data-icon="inline-start" />
                Filters
                {activeCount > 0 ? <span className={countBadgeClassName}>{activeCount}</span> : null}
              </PopoverTrigger>
              <PopoverContent align="end" className="w-80 gap-3 p-3">
                <p className="text-xs font-medium text-muted-foreground">Show only</p>
                <SwitchRow
                  label={MINE_LABEL}
                  description="Bookings you made"
                  checked={calendar.mine}
                  onCheckedChange={calendar.setMine}
                />
                <SwitchRow
                  label={MY_ITEMS_LABEL}
                  description="Bookings on items you own"
                  checked={calendar.myItems}
                  onCheckedChange={calendar.setMyItems}
                />
                <div className="h-px bg-border" />
                <p className="text-xs font-medium text-muted-foreground">
                  {calendar.filters.length === 0
                    ? "No other filters"
                    : `${calendar.filters.length} other filter${calendar.filters.length === 1 ? "" : "s"}`}
                </p>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setFiltersOpen(false);
                    setPanelOpen(true);
                  }}
                >
                  Open filter builder
                </Button>
              </PopoverContent>
            </Popover>
            <ResetButton calendar={calendar} />
          </div>
        </div>
      </div>
      {/* The controls are now hidden, so what is applied is shown as removable chips. */}
      {activeCount > 0 ? (
        <fieldset className="flex min-w-0 flex-wrap gap-2 pt-3">
          <legend className="sr-only">Applied filters</legend>
          {calendar.mine ? <FilterChip label={MINE_LABEL} onRemove={() => calendar.setMine(false)} /> : null}
          {calendar.myItems ? <FilterChip label={MY_ITEMS_LABEL} onRemove={() => calendar.setMyItems(false)} /> : null}
          {calendar.filters.map((filter) => (
            <FilterChip key={filter.id} label={filter.label} onRemove={() => calendar.removeFilter(filter.id)} />
          ))}
        </fieldset>
      ) : null}
      {panelOpen ? <MockFilterPanel calendar={calendar} onClose={() => setPanelOpen(false)} /> : null}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// B. Compact switchers: keep one-click period changes, shrink layout to icons, fold quick filters into a menu.
// ---------------------------------------------------------------------------------------------

export function CompactToolbar({ calendar }: { calendar: CalendarState }) {
  const [panelOpen, setPanelOpen] = useState(false);
  const scoped = calendar.mine || calendar.myItems;
  return (
    <>
      <div role="toolbar" aria-label="Booking events" className="flex flex-col gap-2 border-b py-2">
        <SearchField calendar={calendar} />
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <DateNavigation calendar={calendar} />
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Segmented
              legend="Period"
              options={calendarViews}
              value={calendar.period}
              labels={PERIOD_LABELS}
              disabledReason={monthReason(calendar)}
              onChange={calendar.setPeriod}
            />
            <Segmented
              legend="Layout"
              options={calendarLayouts}
              value={calendar.layout}
              labels={LAYOUT_LABELS}
              icons={LAYOUT_ICONS}
              iconOnly
              onChange={calendar.setLayout}
            />
            <Menu>
              <MenuTrigger
                render={
                  <Button
                    type="button"
                    variant={scoped ? "secondary" : "outline"}
                    aria-label={`Show: ${showSummary(calendar)}`}
                  />
                }
              >
                {showSummary(calendar)}
                <ChevronDownIcon aria-hidden="true" data-icon="inline-end" />
              </MenuTrigger>
              <MenuContent className="w-60">
                <MenuPrimitive.Group>
                  <MenuPrimitive.GroupLabel className={menuGroupLabelClassName}>Show only</MenuPrimitive.GroupLabel>
                  <CheckboxMenuItem checked={calendar.mine} onCheckedChange={calendar.setMine}>
                    <CalendarCheck2Icon aria-hidden="true" className="size-4 text-muted-foreground" />
                    {MINE_LABEL}
                  </CheckboxMenuItem>
                  <CheckboxMenuItem checked={calendar.myItems} onCheckedChange={calendar.setMyItems}>
                    <PackageCheckIcon aria-hidden="true" className="size-4 text-muted-foreground" />
                    {MY_ITEMS_LABEL}
                  </CheckboxMenuItem>
                </MenuPrimitive.Group>
              </MenuContent>
            </Menu>
            <Button
              type="button"
              aria-label={calendar.filters.length > 0 ? `${calendar.filters.length} filters applied` : "Filters"}
              aria-expanded={panelOpen}
              variant={panelOpen || calendar.filters.length > 0 ? "secondary" : "outline"}
              size={calendar.filters.length > 0 ? "default" : "icon"}
              onClick={() => setPanelOpen(!panelOpen)}
            >
              <ListFilterIcon aria-hidden="true" />
              {calendar.filters.length > 0 ? (
                <span className={countBadgeClassName}>{calendar.filters.length}</span>
              ) : null}
            </Button>
            <ResetButton calendar={calendar} />
          </div>
        </div>
      </div>
      {panelOpen ? <MockFilterPanel calendar={calendar} onClose={() => setPanelOpen(false)} /> : null}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// C. Tabs and chips: what you look at (layout tabs + period) sits on the calendar; what you filter sits in a chip row.
// ---------------------------------------------------------------------------------------------

function ChipToggle({
  pressed,
  label,
  onPressedChange,
}: {
  pressed: boolean;
  label: string;
  onPressedChange: (pressed: boolean) => void;
}) {
  return (
    <Button
      type="button"
      size="xs"
      aria-pressed={pressed}
      variant={pressed ? "secondary" : "outline"}
      className={cn("rounded-sm", pressed && "border-border")}
      onClick={() => onPressedChange(!pressed)}
    >
      {pressed ? <CheckIcon aria-hidden="true" /> : null}
      {label}
    </Button>
  );
}

export function TabsAndChipsToolbar({ calendar }: { calendar: CalendarState }) {
  const [panelOpen, setPanelOpen] = useState(false);
  return (
    <>
      <div role="toolbar" aria-label="Booking events" className="flex flex-col gap-2 border-b py-2">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <DateNavigation calendar={calendar} />
          <SearchField calendar={calendar} />
        </div>
        <fieldset className="flex min-w-0 flex-wrap items-center gap-1.5">
          <legend className="sr-only">Filters</legend>
          <ChipToggle pressed={calendar.mine} label={MINE_LABEL} onPressedChange={calendar.setMine} />
          <ChipToggle pressed={calendar.myItems} label={MY_ITEMS_LABEL} onPressedChange={calendar.setMyItems} />
          {calendar.filters.map((filter) => (
            <FilterChip key={filter.id} label={filter.label} onRemove={() => calendar.removeFilter(filter.id)} />
          ))}
          <Button
            type="button"
            size="xs"
            variant="ghost"
            aria-expanded={panelOpen}
            onClick={() => setPanelOpen(!panelOpen)}
          >
            <PlusIcon aria-hidden="true" />
            Filter
          </Button>
          {calendar.hasChanges ? (
            <Button type="button" size="xs" variant="link" className="ml-auto" onClick={calendar.reset}>
              Reset view
            </Button>
          ) : null}
        </fieldset>
      </div>
      {panelOpen ? <MockFilterPanel calendar={calendar} onClose={() => setPanelOpen(false)} /> : null}
    </>
  );
}

export function LayoutTabs({ calendar, children }: { calendar: CalendarState; children: ReactNode }) {
  return (
    <TabsPrimitive.Root
      value={calendar.layout}
      onValueChange={(value) => isLayout(value) && calendar.setLayout(value)}
      className="pt-3"
    >
      <div className="flex flex-wrap items-end justify-between gap-2 border-b">
        <TabsPrimitive.List aria-label="Layout" className="relative flex gap-1">
          {calendarLayouts.map((option) => {
            const Icon = LAYOUT_ICONS[option];
            return (
              <TabsPrimitive.Tab
                key={option}
                value={option}
                className="-mb-px inline-flex h-9 items-center gap-1.5 border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring data-active:border-primary data-active:text-foreground"
              >
                <Icon aria-hidden="true" className="size-4" />
                {LAYOUT_LABELS[option]}
              </TabsPrimitive.Tab>
            );
          })}
        </TabsPrimitive.List>
        <div className="pb-1.5">
          <Segmented
            legend="Period"
            options={calendarViews}
            value={calendar.period}
            labels={PERIOD_LABELS}
            disabledReason={monthReason(calendar)}
            onChange={calendar.setPeriod}
          />
        </div>
      </div>
      {calendarLayouts.map((option) => (
        <TabsPrimitive.Panel key={option} value={option} className="outline-none">
          {calendar.layout === option ? children : null}
        </TabsPrimitive.Panel>
      ))}
    </TabsPrimitive.Root>
  );
}

// ---------------------------------------------------------------------------------------------
// A plain calendar body that only reflects the shared state; the chrome is what is being compared.
// ---------------------------------------------------------------------------------------------

const FIRST_HOUR = 8;
const HOURS = 10;

function EventBlock({ event, style }: { event: MockEvent; style: CSSProperties }) {
  return (
    <div
      className={cn(
        "absolute overflow-hidden rounded-sm border px-1.5 py-0.5 text-xs",
        event.mine ? "border-primary/40 bg-primary/10" : "bg-muted",
      )}
      style={style}
      title={`${event.item} · ${event.purpose} · ${event.bookedBy}`}
    >
      <span className="block truncate font-medium">{event.purpose}</span>
      <span className="block truncate text-muted-foreground">{event.bookedBy}</span>
    </div>
  );
}

export function MockCalendarBody({ calendar }: { calendar: CalendarState }) {
  const events = visibleEvents(calendar);
  const days = calendar.period === "day" ? 1 : 7;
  const items = ITEMS.filter((item) => !calendar.myItems || item.owned);
  return (
    <div className="space-y-3 pt-3">
      <p className="text-sm text-muted-foreground">
        {viewSummary(calendar)} · {dayLabel(calendar.date, 0)}
        {days > 1 ? ` – ${dayLabel(calendar.date, days - 1)}` : ""} · {events.length} event
        {events.length === 1 ? "" : "s"}
      </p>
      {calendar.layout === "resources" ? (
        <div className="divide-y rounded-sm border bg-card">
          {items.map((item) => (
            <div key={item.name} className="flex items-center gap-3 px-3 py-2">
              <span className="w-40 shrink-0 truncate text-sm font-medium">{item.name}</span>
              <div className="relative h-10 flex-1 rounded-sm bg-muted/40">
                {events
                  .filter((event) => event.item === item.name)
                  .map((event) => (
                    <EventBlock
                      key={event.id}
                      event={event}
                      style={{
                        top: 2,
                        bottom: 2,
                        left: `${((event.day + (event.start - FIRST_HOUR) / HOURS) / days) * 100}%`,
                        width: `${((event.end - event.start) / HOURS / days) * 100}%`,
                      }}
                    />
                  ))}
              </div>
            </div>
          ))}
        </div>
      ) : null}
      {calendar.layout === "time-grid" && calendar.period !== "month" ? (
        <div
          className="grid rounded-sm border bg-card"
          style={{ gridTemplateColumns: `repeat(${days}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: days }, (_, day) => (
            <div key={dayLabel(calendar.date, day)} className="border-r last:border-r-0">
              <p className="border-b px-2 py-1 text-xs font-medium">{dayLabel(calendar.date, day)}</p>
              <div className="relative h-64">
                {events
                  .filter((event) => event.day === day)
                  .map((event) => (
                    <EventBlock
                      key={event.id}
                      event={event}
                      style={{
                        left: 4,
                        right: 4,
                        top: `${((event.start - FIRST_HOUR) / HOURS) * 100}%`,
                        height: `${((event.end - event.start) / HOURS) * 100}%`,
                      }}
                    />
                  ))}
              </div>
            </div>
          ))}
        </div>
      ) : null}
      {calendar.layout === "time-grid" && calendar.period === "month" ? (
        <div className="grid grid-cols-7 rounded-sm border bg-card">
          {Array.from({ length: 35 }, (_, day) => (
            <div key={dayLabel(calendar.date, day)} className="h-16 border-r border-b p-1 text-xs">
              <span className="text-muted-foreground">{dayLabel(calendar.date, day)}</span>
              {events
                .filter((event) => event.day === day)
                .map((event) => (
                  <span key={event.id} className="block truncate">
                    {event.purpose}
                  </span>
                ))}
            </div>
          ))}
        </div>
      ) : null}
      {calendar.layout === "agenda" ? (
        <ul className="divide-y rounded-sm border bg-card">
          {events.length === 0 ? <li className="px-3 py-2 text-sm text-muted-foreground">No booking events</li> : null}
          {events.map((event) => (
            <li key={event.id} className="flex flex-wrap gap-x-3 px-3 py-2 text-sm">
              <span className="w-28 text-muted-foreground">{dayLabel(calendar.date, event.day)}</span>
              <span className="font-medium">{event.purpose}</span>
              <span>{event.item}</span>
              <span className="text-muted-foreground">{event.bookedBy}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
