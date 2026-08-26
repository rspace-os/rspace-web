// PROTOTYPE — iterations on variant A ("time first") of the DayTimeline expanded card.
// A is the untouched baseline; A1/A2/A3/A4 keep the time-first hierarchy and disagree about
// chrome, meta layout and where the primary action lives. Switch with ?variant=A|A1|A2|A3|A4.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Popover } from "@base-ui/react/popover";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import {
  ArrowLeft,
  ArrowRight,
  Ban,
  CalendarDays,
  Check,
  ChevronRight,
  Copy,
  ExternalLink,
  Hourglass,
  MapPin,
  MoreHorizontal,
  Pencil,
  Trash2,
  Wrench,
  X,
} from "lucide-react";
import * as React from "react";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { cn } from "@/modules/common/utils/cn";

const booking = {
  date: "Wednesday, 22 July",
  end: "11:00",
  item: "Confocal microscope",
  itemGlobalId: "IC-LSM900",
  itemHref: "/inventory/IC-LSM900",
  itemLocation: "Plant Sciences, room 2.14",
  blockoutNotes: "Annual laser realignment and objective service. Instrument unavailable all morning.",
  notes: "Confocal imaging of plate 4. Use the 63x oil objective and leave the chamber at 37 °C.",
  person: "Ada Lovelace",
  start: "09:30",
};

const variants = [
  {
    key: "A",
    name: "Time first (baseline)",
    width: "24rem",
    chrome: "tinted header band",
    primary: "footer text actions",
  },
  { key: "A1", name: "Real width", width: "18rem (in place)", chrome: "none", primary: "split button row" },
  {
    key: "A2",
    name: "Fact sheet",
    width: "22rem",
    chrome: "header band, actions inside",
    primary: "header icon buttons",
  },
  { key: "A3", name: "Bottom-anchored primary", width: "20rem", chrome: "colour spine", primary: "full-width button" },
  {
    key: "A4",
    name: "Fact sheet + split row",
    width: "22rem",
    chrome: "header band, time at text-lg",
    primary: "split button row",
  },
] as const;

type VariantKey = (typeof variants)[number]["key"];

/**
 * The three event shapes A4 has to carry. "maintenance" is DayTimeline's `kind: "blockout"`,
 * which has a title instead of a person and gets no actions at all — DayTimeline.tsx only
 * calls renderEventActions for bookings. "unconfirmed" has no backend equivalent yet:
 * BookingState is CONFIRMED | CANCELLED, so this one is exploring a state, not rendering one.
 */
type CardKind = "confirmed" | "unconfirmed" | "maintenance";

const KIND_STYLES = {
  confirmed: {
    card: "border-blue-700 ring-blue-500/15",
    header: "bg-blue-700 text-white",
    meta: "text-blue-100",
    control: "hover:bg-white/15",
    eyebrow: null,
    icon: null,
  },
  unconfirmed: {
    card: "border-blue-500 border-dashed ring-blue-500/10",
    header: "border-blue-300 border-b border-dashed bg-blue-50 text-blue-950",
    meta: "text-blue-700",
    control: "hover:bg-blue-100",
    eyebrow: "Awaiting confirmation",
    icon: Hourglass,
  },
  maintenance: {
    card: "border-amber-600 ring-amber-500/15",
    header: "border-amber-300 border-b bg-amber-100 text-amber-950",
    meta: "text-amber-800",
    control: "hover:bg-amber-200",
    eyebrow: "Scheduled maintenance",
    icon: Wrench,
  },
} as const satisfies Record<CardKind, unknown>;

type CardAction = { key: string; label: string; icon: React.ComponentType<{ className?: string }> };

const EDIT_ACTION: CardAction = { key: "edit", label: "Edit", icon: Pencil };
const CONFIRM_ACTION: CardAction = { key: "confirm", label: "Confirm", icon: Check };
const DECLINE_ACTION: CardAction = { key: "decline", label: "Decline", icon: Ban };
const OPEN_ACTION: CardAction = { key: "open", label: "Open", icon: ExternalLink };

/** Extra actions exist only to stress the row; the real card ships with two. */
const STRESS_ACTIONS: ReadonlyArray<CardAction> = [
  EDIT_ACTION,
  OPEN_ACTION,
  { key: "duplicate", label: "Duplicate", icon: Copy },
  { key: "cancel", label: "Cancel", icon: Ban },
  { key: "delete", label: "Delete", icon: Trash2 },
];

function PrototypeAction({ children, destructive = false }: { children: React.ReactNode; destructive?: boolean }) {
  return (
    <button
      type="button"
      className={
        destructive
          ? "rounded-md px-2.5 py-1.5 font-medium text-red-700 text-xs hover:bg-red-50 focus-visible:outline-2 focus-visible:outline-red-600"
          : "rounded-md px-2.5 py-1.5 font-medium text-slate-700 text-xs hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600"
      }
    >
      {children}
    </button>
  );
}

/** Baseline, visually verbatim so the iterations can be judged against it (caret now wired). */
function VariantA({ canEdit, onCollapse }: { canEdit: boolean; onCollapse: () => void }) {
  return (
    <article className="w-[24rem] overflow-hidden rounded-lg border border-blue-700 bg-white text-slate-950 shadow-xl ring-4 ring-blue-500/15">
      <header className="flex items-start justify-between gap-4 border-blue-100 border-b bg-blue-50 px-4 py-3">
        <div className="min-w-0">
          <p className="font-medium text-blue-800 text-xs uppercase tracking-wide">Confirmed booking</p>
          <h3 className="truncate font-semibold text-base">{booking.person}</h3>
        </div>
        <button
          type="button"
          aria-label="Collapse booking details"
          aria-expanded={true}
          onClick={onCollapse}
          className="grid size-8 shrink-0 place-items-center rounded-full text-slate-600 hover:bg-white"
        >
          <X className="size-4" />
        </button>
      </header>

      <div className="space-y-4 px-4 py-4">
        <div>
          <p className="font-bold text-3xl tabular-nums tracking-tight">
            {booking.start} <span className="font-normal text-slate-400">to</span> {booking.end}
          </p>
          <p className="mt-1 flex items-center gap-1.5 text-slate-600 text-sm">
            <CalendarDays className="size-4" /> {booking.date} <span aria-hidden="true">·</span> 1 hr 30 min
          </p>
        </div>

        <div className="rounded-md bg-slate-50 px-3 py-2.5">
          <p className="mb-1 font-medium text-slate-500 text-xs">Purpose and setup</p>
          <p className="text-sm leading-5">{booking.notes}</p>
        </div>
      </div>

      <footer className="flex items-center justify-between border-slate-200 border-t px-3 py-2">
        <span className="flex items-center gap-1.5 font-medium text-emerald-700 text-xs">
          {canEdit && (
            <>
              <Check className="size-3.5" /> You can edit
            </>
          )}
        </span>
        <div className="flex items-center">
          {canEdit && <PrototypeAction>Edit</PrototypeAction>}
          <PrototypeAction>Open</PrototypeAction>
        </div>
      </footer>
    </article>
  );
}

/**
 * A1 — A's hierarchy at the width the real card actually has (w-72, expanding in place).
 * No header band: the status becomes one inline line, the time is the headline, and the
 * two actions share a split row so nothing is truncated at 18rem.
 */
function VariantA1({ canEdit, onCollapse }: { canEdit: boolean; onCollapse: () => void }) {
  return (
    <article className="w-72 overflow-hidden rounded-md border border-blue-700 bg-white text-slate-950 shadow-xl ring-4 ring-blue-500/15">
      <div className="flex items-start justify-between gap-2 px-3 pt-2.5">
        <span className="flex min-w-0 items-center gap-1.5 font-medium text-emerald-700 text-xs">
          <span className="size-1.5 shrink-0 rounded-full bg-emerald-600" aria-hidden="true" />
          <span className="truncate">{canEdit ? "Confirmed · you can edit" : "Confirmed"}</span>
        </span>
        <button
          type="button"
          aria-label="Collapse booking details"
          aria-expanded={true}
          onClick={onCollapse}
          className="-mr-1 grid size-6 shrink-0 place-items-center rounded-full text-slate-500 hover:bg-slate-100"
        >
          <X className="size-4" />
        </button>
      </div>

      <div className="px-3 pt-1.5 pb-3">
        <p className="font-bold text-2xl tabular-nums leading-tight tracking-tight">
          {booking.start}
          <span className="px-1 font-normal text-slate-400">–</span>
          {booking.end}
        </p>
        <p className="mt-0.5 truncate text-slate-500 text-xs">{booking.date} · 1 hr 30 min</p>
        <p className="mt-2 truncate font-medium text-sm">{booking.person}</p>
        <p className="mt-1 line-clamp-3 text-slate-600 text-xs leading-4">{booking.notes}</p>
      </div>

      <div
        className={`grid divide-slate-200 border-slate-200 border-t text-xs ${
          canEdit ? "grid-cols-2 divide-x" : "grid-cols-1"
        }`}
      >
        {canEdit && (
          <button type="button" className="flex items-center justify-center gap-1.5 py-2 font-medium hover:bg-slate-50">
            <Pencil className="size-3.5" /> Edit
          </button>
        )}
        <button type="button" className="flex items-center justify-center gap-1.5 py-2 font-medium hover:bg-slate-50">
          <ExternalLink className="size-3.5" /> Open
        </button>
      </div>
    </article>
  );
}

/**
 * A2 — the time is promoted into the header band itself, so the body is free to be a
 * scannable fact sheet instead of a notes box. Actions are icon buttons in the header,
 * which removes the footer entirely.
 */
function VariantA2({ canEdit, onCollapse }: { canEdit: boolean; onCollapse: () => void }) {
  return (
    <article className="w-[22rem] overflow-hidden rounded-lg border border-blue-700 bg-white text-slate-950 shadow-xl ring-4 ring-blue-500/15">
      <header className="flex items-start justify-between gap-3 bg-blue-700 px-4 py-3 text-white">
        <div className="min-w-0">
          <p className="font-bold text-2xl tabular-nums leading-tight tracking-tight">
            {booking.start}
            <span className="px-1 font-normal text-blue-200">–</span>
            {booking.end}
          </p>
          <p className="mt-0.5 truncate text-blue-100 text-xs">{booking.date} · 1 hr 30 min</p>
        </div>
        <div className="flex shrink-0 items-center gap-0.5">
          {canEdit && (
            <button
              type="button"
              aria-label="Edit booking"
              className="grid size-7 place-items-center rounded-md hover:bg-white/15"
            >
              <Pencil className="size-4" />
            </button>
          )}
          <button
            type="button"
            aria-label="Open booking"
            className="grid size-7 place-items-center rounded-md hover:bg-white/15"
          >
            <ExternalLink className="size-4" />
          </button>
          <button
            type="button"
            aria-label="Collapse booking details"
            aria-expanded={true}
            onClick={onCollapse}
            className="grid size-7 place-items-center rounded-md hover:bg-white/15"
          >
            <X className="size-4" />
          </button>
        </div>
      </header>

      <dl className="divide-y divide-slate-100 px-4 text-sm">
        <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
          <dt className="text-slate-500 text-xs">Booked by</dt>
          <dd className="truncate font-medium">{booking.person}</dd>
        </div>
        <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
          <dt className="text-slate-500 text-xs">Item</dt>
          <dd className="truncate">{booking.item}</dd>
        </div>
        <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
          <dt className="text-slate-500 text-xs">Purpose</dt>
          <dd className="text-xs leading-4">{booking.notes}</dd>
        </div>
      </dl>
    </article>
  );
}

/**
 * A4 — A2's header band and fact-sheet body with A1's split action row instead of header
 * icon buttons, so the header carries only the time and the collapse control. The time is
 * set lighter than in A2 (semibold at text-lg) to stop it shouting over the facts below.
 */
function VariantA4({
  canEdit,
  onCollapse,
  actions,
  overflow,
  name = booking.item,
  location = booking.itemLocation,
  idPlacement = "description",
  width = "22rem",
  kind = "confirmed",
}: {
  canEdit: boolean;
  onCollapse: () => void;
  actions?: ReadonlyArray<CardAction>;
  /** Rendered behind a single "More" cell so the row width stops growing. */
  overflow?: ReadonlyArray<CardAction>;
  name?: string;
  location?: string;
  /**
   * Where the global-ID badge sits. "own-line" always drops it below the name, in pure
   * CSS via flex-wrap plus a full-row name.
   */
  idPlacement?: "description" | "title" | "own-line";
  width?: string;
  kind?: CardKind;
}) {
  const style = KIND_STYLES[kind];
  const Eyebrow = style.icon;
  // Blockouts get no actions in the real component, so maintenance gets no row.
  const defaultActions =
    kind === "maintenance"
      ? []
      : kind === "unconfirmed"
        ? canEdit
          ? [CONFIRM_ACTION, DECLINE_ACTION]
          : [OPEN_ACTION]
        : canEdit
          ? [EDIT_ACTION, OPEN_ACTION]
          : [OPEN_ACTION];
  const rowActions = actions ?? defaultActions;
  const cells = rowActions.length + (overflow?.length ? 1 : 0);
  return (
    <article
      style={{ width }}
      className={cn("overflow-hidden rounded-lg border bg-white text-slate-950 shadow-xl ring-4", style.card)}
    >
      <header className={cn("flex items-start justify-between gap-3 px-4 py-2.5", style.header)}>
        <div className="min-w-0">
          {style.eyebrow ? (
            <p className={cn("flex items-center gap-1.5 font-medium text-[11px] uppercase tracking-wide", style.meta)}>
              {Eyebrow ? <Eyebrow className="size-3" /> : null}
              {style.eyebrow}
            </p>
          ) : null}
          <p className="font-semibold text-lg tabular-nums leading-tight">
            {booking.start}
            <span className={cn("px-1 font-normal", style.meta)}>–</span>
            {booking.end}
          </p>
          <p className={cn("mt-0.5 truncate text-xs", style.meta)}>{booking.date} · 1 hr 30 min</p>
        </div>
        <button
          type="button"
          aria-label="Collapse booking details"
          aria-expanded={true}
          onClick={onCollapse}
          className={cn("-mr-1 grid size-7 shrink-0 place-items-center rounded-md", style.control)}
        >
          <X className="size-4" />
        </button>
      </header>

      <dl className="divide-y divide-slate-100 px-4 text-sm">
        <div className="py-2">
          <dt className="sr-only">Item</dt>
          <dd className="@container">
            <InventoryItem
              name={name}
              globalId={booking.itemGlobalId}
              href={booking.itemHref}
              idLinkLabel={`Open inventory record ${booking.itemGlobalId}`}
              idPlacement={idPlacement === "description" ? "description" : "title"}
              className={cn(
                "p-0",
                // ponytail: the name clamp is bolted on from outside because
                // InventoryItem hardcodes line-clamp-1 on ItemTitle and truncate on
                // the name span. Fold-in is a `nameLines` prop on InventoryItem, not
                // this selector soup.
                // line-clamp-none would set display:block and break ItemTitle's flex row,
                // dropping the badge onto its own line. Unset the clamp only.
                "[&_[data-slot=item-title]]:flex! [&_[data-slot=item-title]]:items-start",
                "[&_[data-slot=item-title]]:overflow-visible [&_[data-slot=item-title]]:[-webkit-line-clamp:unset]",
                "[&_[data-slot=item-title]>span:first-child]:line-clamp-1",
                "[&_[data-slot=item-title]>span:first-child]:whitespace-normal",
                "@[19rem]:[&_[data-slot=item-title]>span:first-child]:line-clamp-2",
                // The name claims the whole row, so the badge always wraps beneath it.
                idPlacement === "own-line" && "[&_[data-slot=item-title]]:flex-wrap",
                idPlacement === "own-line" && "[&_[data-slot=item-title]>span:first-child]:basis-full",
              )}
            >
              <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
              {location}
            </InventoryItem>
          </dd>
        </div>
        {kind !== "maintenance" && (
          <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
            <dt className="text-slate-500 text-xs">Booked by</dt>
            <dd className="truncate font-medium">{booking.person}</dd>
          </div>
        )}
        <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
          <dt className="text-slate-500 text-xs">{kind === "maintenance" ? "Notes" : "Purpose"}</dt>
          <dd className="text-xs leading-4">{kind === "maintenance" ? booking.blockoutNotes : booking.notes}</dd>
        </div>
      </dl>

      <div
        hidden={cells === 0}
        className={`grid divide-slate-200 border-slate-200 border-t text-xs ${cells > 1 ? "divide-x" : ""}`}
        style={{ gridTemplateColumns: `repeat(${cells}, minmax(0, 1fr))` }}
      >
        {rowActions.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            className="flex items-center justify-center gap-1.5 py-2 font-medium hover:bg-slate-50"
          >
            <Icon className="size-3.5" /> {label}
          </button>
        ))}
        {overflow?.length ? (
          <Menu>
            <MenuTrigger className="flex items-center justify-center gap-1.5 py-2 font-medium text-xs hover:bg-slate-50">
              <MoreHorizontal className="size-3.5" /> More
            </MenuTrigger>
            <MenuContent className="w-52">
              {overflow.map(({ key, label, icon: Icon }) => (
                <MenuItem key={key} onClick={() => {}}>
                  <Icon className="size-4" /> {label}
                </MenuItem>
              ))}
            </MenuContent>
          </Menu>
        ) : null}
      </div>
    </article>
  );
}

/**
 * A3 — no header band at all; a colour spine carries the identity so the body starts at
 * the time. One full-width primary action is pinned at the bottom and everything else
 * hides behind an overflow menu, which makes the expected next step unambiguous.
 */
function VariantA3({ canEdit, onCollapse }: { canEdit: boolean; onCollapse: () => void }) {
  return (
    <article className="flex w-[20rem] overflow-hidden rounded-lg border border-slate-300 bg-white text-slate-950 shadow-xl ring-4 ring-blue-500/15">
      <div className="w-1.5 shrink-0 bg-blue-600" aria-hidden="true" />

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2 px-3.5 pt-3">
          <div className="min-w-0">
            <p className="font-bold text-3xl tabular-nums leading-none tracking-tight">{booking.start}</p>
            <p className="mt-1 text-slate-500 text-sm">
              until {booking.end} <span aria-hidden="true">·</span> 1 hr 30 min
            </p>
          </div>
          <div className="flex shrink-0 items-center">
            <button
              type="button"
              aria-label="More booking actions"
              className="grid size-7 place-items-center rounded-full text-slate-500 hover:bg-slate-100"
            >
              <MoreHorizontal className="size-4" />
            </button>
            <button
              type="button"
              aria-label="Collapse booking details"
              aria-expanded={true}
              onClick={onCollapse}
              className="-mr-1 grid size-7 place-items-center rounded-full text-slate-500 hover:bg-slate-100"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        <p className="mt-2.5 px-3.5 font-medium text-sm">
          {booking.person}
          <span className="ml-1.5 font-normal text-slate-500">· {booking.date}</span>
        </p>
        <p className="mt-1 px-3.5 pb-3 text-slate-600 text-xs leading-4">{booking.notes}</p>

        <div className="px-3 pb-3">
          <Button type="button" size="sm" className="w-full">
            {canEdit ? (
              <>
                <Pencil /> Edit this booking
              </>
            ) : (
              <>
                <ExternalLink /> Open booking
              </>
            )}
          </Button>
        </div>
      </div>
    </article>
  );
}

function PrototypeSwitcher({
  current,
  select,
  canEdit,
  toggleCanEdit,
}: {
  current: VariantKey;
  select: (variant: VariantKey) => void;
  canEdit: boolean;
  toggleCanEdit: () => void;
}) {
  const index = variants.findIndex(({ key }) => key === current);
  const move = React.useCallback(
    (direction: -1 | 1) => select(variants[(index + direction + variants.length) % variants.length].key),
    [index, select],
  );

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.target instanceof HTMLInputElement ||
        event.target instanceof HTMLTextAreaElement ||
        (event.target instanceof HTMLElement && event.target.isContentEditable)
      )
        return;
      if (event.key === "ArrowLeft") move(-1);
      if (event.key === "ArrowRight") move(1);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [move]);

  if (import.meta.env.PROD) return null;
  return (
    <nav
      aria-label="Prototype variants"
      className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-1 rounded-full bg-slate-950 p-1.5 text-white shadow-2xl"
    >
      <button
        type="button"
        aria-label="Previous prototype"
        onClick={() => move(-1)}
        className="grid size-9 place-items-center rounded-full hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
      >
        <ArrowLeft className="size-4" />
      </button>
      <span className="min-w-56 px-2 text-center font-medium text-sm">
        {current} · {variants[index].name}
      </span>
      <button
        type="button"
        aria-label="Next prototype"
        onClick={() => move(1)}
        className="grid size-9 place-items-center rounded-full hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
      >
        <ArrowRight className="size-4" />
      </button>
      <span className="mx-1 h-6 w-px bg-white/25" aria-hidden="true" />
      <button
        type="button"
        aria-pressed={canEdit}
        onClick={toggleCanEdit}
        className="rounded-full px-3 py-1.5 font-medium text-xs hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white aria-pressed:bg-white aria-pressed:text-slate-950"
      >
        canEdit
      </button>
    </nav>
  );
}

/**
 * The collapsed state every variant returns to: the booking's own chip on the timeline,
 * sized to its 09:30–11:00 slot. Modelled on the real DayTimelineEventCard, where the
 * caret only fades in on hover or focus so the compact row stays quiet.
 */
function CollapsedChip({ onExpand }: { onExpand: () => void }) {
  return (
    <div className="group absolute top-4 left-[25%] flex h-9 w-[25%] flex-col justify-center overflow-hidden rounded-md border border-blue-700 bg-blue-600 px-2 py-1 text-white text-xs shadow-sm">
      <span className="block truncate pr-6 font-semibold">{booking.person}</span>
      <span>{booking.start}</span>
      <button
        type="button"
        aria-label="Show booking details"
        aria-expanded={false}
        onClick={onExpand}
        className="absolute top-1/2 right-1 grid size-6 -translate-y-1/2 place-items-center rounded-full border border-white/30 bg-white/15 opacity-0 hover:bg-white/25 focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-white group-hover:opacity-100"
      >
        <ChevronRight className="size-3.5" />
      </button>
    </div>
  );
}

function TimelineContext() {
  const [variant, setVariant] = React.useState<VariantKey>(() => {
    const requested = new URLSearchParams(window.location.search).get("variant");
    return variants.some(({ key }) => key === requested) ? (requested as VariantKey) : "A";
  });
  const current = variants.find(({ key }) => key === variant) ?? variants[0];

  // The prototype is about the expanded card, so a shared ?variant= link lands on it open.
  const [expanded, setExpanded] = React.useState(true);
  const [canEdit, setCanEdit] = React.useState(true);
  const stageRef = React.useRef<HTMLDivElement>(null);
  const restoreFocus = React.useRef(false);

  const toggle = React.useCallback((next: boolean) => {
    restoreFocus.current = true;
    setExpanded(next);
  }, []);

  // Collapsing and expanding swap the whole subtree, so move focus onto whichever
  // disclosure control replaced the one that was just clicked.
  React.useEffect(() => {
    if (!restoreFocus.current) return;
    restoreFocus.current = false;
    stageRef.current?.querySelector<HTMLElement>("[aria-expanded]")?.focus();
  }, [expanded]);

  const collapse = React.useCallback(() => toggle(false), [toggle]);
  const expand = React.useCallback(() => toggle(true), [toggle]);

  const select = React.useCallback((next: VariantKey) => {
    const url = new URL(window.location.href);
    url.searchParams.set("variant", next);
    window.history.replaceState({}, "", url);
    setVariant(next);
  }, []);

  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-6xl">
        <header className="mb-7">
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card prototype — round 2</p>
          <h1 className="mt-1 font-semibold text-2xl">Variant A won on hierarchy. Which shape does it keep?</h1>
          <p className="mt-1 text-slate-600 text-sm">
            A is the baseline. A1 to A4 keep time-first and disagree about chrome, meta layout and where the primary
            action lives. Use the arrows or left and right keys to compare, and the caret to collapse the card back to
            its chip on the timeline.
          </p>
        </header>

        <section
          aria-label="Timeline context"
          className="overflow-hidden rounded-xl border border-slate-300 bg-white shadow-sm"
        >
          <div className="grid h-10 grid-cols-6 border-slate-200 border-b bg-slate-50 text-slate-500 text-xs">
            {["08:00", "09:00", "10:00", "11:00", "12:00", "13:00"].map((time) => (
              <time key={time} className="border-slate-200 border-l px-2 pt-3 first:border-l-0">
                {time}
              </time>
            ))}
          </div>
          <div
            ref={stageRef}
            className="relative flex min-h-[27rem] items-start justify-center bg-[linear-gradient(to_right,transparent_calc(100%/6_-_1px),rgb(226_232_240)_calc(100%/6_-_1px))] bg-[length:calc(100%/6)_100%] px-8 pt-14"
          >
            <div className="absolute top-4 left-[8%] h-9 w-[13%] rounded-md border border-slate-400 bg-slate-200 px-2 py-1 text-slate-700 text-xs shadow-sm">
              <span className="block truncate font-semibold">Busy</span>
              <span>08:30</span>
            </div>
            <div className="absolute top-4 left-[72%] h-9 w-[18%] rounded-md border border-amber-600 bg-amber-100 px-2 py-1 text-amber-950 text-xs shadow-sm">
              <span className="block truncate font-semibold">Maintenance</span>
              <span>12:20</span>
            </div>
            {!expanded && <CollapsedChip onExpand={expand} />}
            {expanded && variant === "A" && <VariantA canEdit={canEdit} onCollapse={collapse} />}
            {expanded && variant === "A1" && <VariantA1 canEdit={canEdit} onCollapse={collapse} />}
            {expanded && variant === "A2" && <VariantA2 canEdit={canEdit} onCollapse={collapse} />}
            {expanded && variant === "A3" && <VariantA3 canEdit={canEdit} onCollapse={collapse} />}
            {expanded && variant === "A4" && <VariantA4 canEdit={canEdit} onCollapse={collapse} />}
          </div>
        </section>

        <output className="mt-4 block rounded-sm border border-slate-300 border-dashed bg-white/60 px-3 py-2 font-mono text-slate-600 text-xs">
          variant={current.key}; expanded={String(expanded)}; canEdit={String(canEdit)}; width={current.width}; chrome=
          {current.chrome}; primary={current.primary}
        </output>
      </div>
      <PrototypeSwitcher
        current={variant}
        select={select}
        canEdit={canEdit}
        toggleCanEdit={() => setCanEdit((v) => !v)}
      />
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/DayTimeline expanded card v2",
  component: TimelineContext,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof TimelineContext>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompareVariants: Story = {};

const LONG_NAME = "Zeiss LSM 900 Airyscan 2 inverted laser-scanning confocal microscope with environmental chamber";
const LONG_LOCATION = "Plant Sciences Building, Level 2, East Wing, Room 2.14b, bench 7, behind the cold-room annexe";

/**
 * Stress cases for A4. Each card says what to watch, because most failures here are
 * quiet: text truncates rather than overflowing, so a card can look fine and still
 * have lost the information it was meant to carry.
 */
const stressCases = [
  { id: "baseline", caption: "2 actions, normal content", watch: "Reference shape.", actions: 2 },
  { id: "three", caption: "3 actions", watch: "Labels start crowding the icons.", actions: 3 },
  { id: "four", caption: "4 actions", watch: "~5.5rem per cell at 22rem wide.", actions: 4 },
  { id: "five", caption: "5 actions", watch: "Do labels still fit, or wrap?", actions: 5 },
  {
    id: "long-name",
    caption: "2 actions, very long item name",
    watch: "Name truncates; does the global-ID badge survive?",
    actions: 2,
    name: LONG_NAME,
  },
  {
    id: "long-location",
    caption: "2 actions, very long location",
    watch: "Location truncates on the second line.",
    actions: 2,
    location: LONG_LOCATION,
  },
  {
    id: "worst",
    caption: "5 actions, long name and location",
    watch: "Everything at once.",
    actions: 5,
    name: LONG_NAME,
    location: LONG_LOCATION,
  },
] as const;

function StressGrid() {
  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-7xl">
        <header className="mb-7">
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card — A4 stress cases</p>
          <h1 className="mt-1 font-semibold text-2xl">Where does A4 break?</h1>
          <p className="mt-1 text-slate-600 text-sm">
            The same card at 22rem with a growing action row and overlong inventory content. Booked by is left at its
            normal value because it is being replaced.
          </p>
        </header>

        <ul className="flex flex-wrap gap-6">
          {stressCases.map(({ id, caption, watch, actions, ...content }) => (
            <li key={id} className="space-y-2">
              <div className="max-w-[22rem]">
                <p className="font-medium text-sm">{caption}</p>
                <p className="text-slate-500 text-xs">{watch}</p>
              </div>
              <VariantA4 canEdit onCollapse={() => {}} actions={STRESS_ACTIONS.slice(0, actions)} {...content} />
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}

export const StressA4: Story = {
  render: () => <StressGrid />,
};

const OVERFLOW_ACTIONS = STRESS_ACTIONS.slice(2);

function SolutionSection({
  title,
  rationale,
  children,
}: {
  title: string;
  rationale: string;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3">
      <div className="max-w-2xl">
        <h2 className="font-heading font-semibold text-lg">{title}</h2>
        <p className="text-slate-600 text-sm">{rationale}</p>
      </div>
      <div className="flex flex-wrap items-start gap-6">{children}</div>
    </section>
  );
}

function SolutionsGrid() {
  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-7xl space-y-10">
        <header>
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card — A4 fixes</p>
          <h1 className="mt-1 font-semibold text-2xl">Three fixes for the stress cases</h1>
          <p className="mt-1 max-w-2xl text-slate-600 text-sm">
            The location string is not ours to reformat, so every fix here is layout only.
          </p>
        </header>

        <SolutionSection
          title="1. Action menu caps the row at three cells"
          rationale="Edit and Open stay visible; Duplicate, Cancel and Delete move behind More. The row stops widening
            no matter how many actions arrive later, and the destructive pair is no longer one mis-tap from Edit."
        >
          <VariantA4 canEdit onCollapse={() => {}} overflow={OVERFLOW_ACTIONS} />
          <VariantA4 canEdit onCollapse={() => {}} actions={STRESS_ACTIONS} name="Five flat actions, for contrast" />
        </SolutionSection>

        <SolutionSection
          title='2. idPlacement="title" frees the whole second line for the location'
          rationale="The badge moves up beside the name, so the long location no longer shares its line with the
            global ID. Same height, no component change."
        >
          <VariantA4 canEdit onCollapse={() => {}} location={LONG_LOCATION} />
          <VariantA4 canEdit onCollapse={() => {}} location={LONG_LOCATION} idPlacement="title" />
        </SolutionSection>

        <SolutionSection
          title="3. Name clamp follows the container, not the viewport"
          rationale="One card at three widths in one viewport. Below 19rem of content width the name gets a single
            line; at or above it, two. The real 22rem card measures 19.875rem inside its padding and border, so it
            lands on the two-line side. Resize the window and nothing changes, which is the point."
        >
          {["18rem", "22rem", "28rem"].map((width) => (
            <div key={width} className="space-y-1">
              <p className="font-mono text-slate-500 text-xs">width={width}</p>
              <VariantA4
                canEdit
                onCollapse={() => {}}
                overflow={OVERFLOW_ACTIONS}
                name={LONG_NAME}
                location={LONG_LOCATION}
                idPlacement="title"
                width={width}
              />
            </div>
          ))}
        </SolutionSection>
        <SolutionSection
          title="4. Badge placement modes, short name above long name"
          rationale={`All three are pure CSS. "own-line" gives the name the full row via flex-wrap, so the badge always drops beneath it, at the cost of one line of height on every card.`}
        >
          {(["description", "title", "own-line"] as const).map((mode) => (
            <div key={mode} className="space-y-2">
              <p className="font-mono text-slate-500 text-xs">idPlacement={mode}</p>
              {[booking.item, LONG_NAME].map((name) => (
                <VariantA4
                  key={name}
                  canEdit
                  onCollapse={() => {}}
                  overflow={OVERFLOW_ACTIONS}
                  name={name}
                  location={LONG_LOCATION}
                  idPlacement={mode}
                />
              ))}
            </div>
          ))}
        </SolutionSection>
      </div>
    </main>
  );
}

export const SolutionsA4: Story = {
  render: () => <SolutionsGrid />,
};

const eventKinds = [
  {
    kind: "confirmed" as const,
    caption: "Confirmed booking",
    note: "BookingState.CONFIRMED. Edit and Open, with the rest behind More.",
  },
  {
    kind: "unconfirmed" as const,
    caption: "Unconfirmed booking",
    note: "No backend state for this yet. Dashed edge and a light header read as provisional; the primary pair becomes Confirm and Decline rather than Edit and Open.",
  },
  {
    kind: "maintenance" as const,
    caption: "Maintenance (blockout)",
    note: 'kind: "blockout". Amber, a title instead of a person so Booked by is dropped, and no action row at all because DayTimeline only renders actions for bookings.',
  },
];

function EventKindsGrid() {
  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-7xl">
        <header className="mb-7">
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card — A4 event kinds</p>
          <h1 className="mt-1 font-semibold text-2xl">One card shape, three event kinds</h1>
          <p className="mt-1 max-w-2xl text-slate-600 text-sm">
            Same skeleton throughout: only the chrome, the status eyebrow, the Booked by row and the action row change.
          </p>
        </header>

        <ul className="flex flex-wrap items-start gap-6">
          {eventKinds.map(({ kind, caption, note }) => (
            <li key={kind} className="w-[22rem] space-y-2">
              <div>
                <p className="font-medium text-sm">{caption}</p>
                <p className="text-slate-500 text-xs">{note}</p>
              </div>
              <VariantA4
                canEdit
                onCollapse={() => {}}
                kind={kind}
                idPlacement="title"
                overflow={kind === "confirmed" ? OVERFLOW_ACTIONS : undefined}
              />
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}

export const EventKindsA4: Story = {
  render: () => <EventKindsGrid />,
};

/**
 * The real card sits inside DayTimeline's horizontally scrolling canvas, which is at least
 * 1920px wide, so vw units are useless: the containing block is the canvas, not the screen.
 * The scroller element's own width IS the visible width, so making it a container-query
 * container gives the card access to it in pure CSS. cqw then clamps the card to what the
 * user can actually see.
 */
// No gutter: below 22rem of visible width this resolves to the full window width, so
// "small viewport" needs no breakpoint. It is just "narrower than the preferred width".
const RESPONSIVE_WIDTH = "min(22rem, 100cqw)";

function ScrollerMock({ label, viewport }: { label: string; viewport: string }) {
  return (
    <li className="space-y-2">
      <p className="font-mono text-slate-500 text-xs">scroller={label}</p>
      {/* @container makes this the query container; overflow-x-auto keeps it the visible window. */}
      <div
        className="@container overflow-x-auto rounded-sm border border-slate-300 bg-white"
        style={{ width: viewport }}
      >
        <div className="relative h-72 min-w-[1920px]">
          <div
            className="pointer-events-none absolute inset-0 grid grid-cols-[repeat(24,minmax(0,1fr))]"
            aria-hidden="true"
          >
            {Array.from({ length: 24 }, (_, hour) => (
              <div key={hour} className={`relative border-slate-200 border-l ${hour % 2 === 0 ? "bg-slate-50" : ""}`}>
                <span className="absolute top-1 left-1 text-[10px] text-slate-400">{hour}</span>
              </div>
            ))}
          </div>
          <div className="absolute top-8 left-[10%]">
            <VariantA4
              canEdit
              onCollapse={() => {}}
              idPlacement="title"
              overflow={OVERFLOW_ACTIONS}
              name={LONG_NAME}
              location={LONG_LOCATION}
              width={RESPONSIVE_WIDTH}
            />
          </div>
        </div>
      </div>
    </li>
  );
}

function SmallViewportGrid() {
  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-5xl">
        <header className="mb-7">
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card — small viewports</p>
          <h1 className="mt-1 font-semibold text-2xl">Width clamping is necessary and not sufficient</h1>
          <p className="mt-2 max-w-3xl text-slate-600 text-sm">
            Each box mocks DayTimeline's scroller: a narrow window onto a 1920px canvas. The card is sized{" "}
            <code className="rounded bg-slate-200 px-1 font-mono text-xs">{RESPONSIVE_WIDTH}</code>, which works: it
            takes the full window width at 320px and keeps its preferred 22rem from 480px up, with no breakpoint.
          </p>
          <p className="mt-2 max-w-3xl font-medium text-red-700 text-sm">
            But look at the first two boxes. The card is anchored to its event's offset in the canvas, so it is clipped
            by the right edge of the scroll window however narrow we make it. CSS can size the card to the window; it
            cannot move it into the window, because that needs the scroll offset. Below roughly 480px the
            anchored-floating-card pattern does not work at all and the details want a sheet instead.
          </p>
        </header>

        <ul className="space-y-8">
          <ScrollerMock label="320px (small phone)" viewport="320px" />
          <ScrollerMock label="480px" viewport="480px" />
          <ScrollerMock label="900px (roomy)" viewport="900px" />
        </ul>
      </div>
    </main>
  );
}

export const SmallViewportA4: Story = {
  render: () => <SmallViewportGrid />,
};

/**
 * Popping the card out: instead of living inside the scrolling canvas, it is portalled to
 * the body and anchored to its chip. That changes the geometry completely. The card is no
 * longer clipped by the scroller's overflow, collision avoidance can flip and shift it to
 * stay inside a boundary, --available-width gives it the real room it has, and `sticky`
 * keeps it visible once the chip scrolls away. modal={false} keeps it a disclosure rather
 * than a dialog, so the timeline behind it is not aria-hidden.
 */
function PoppedOutChip({ label, start, boundary }: { label: string; start: string; boundary: HTMLElement | null }) {
  const [open, setOpen] = React.useState(false);
  return (
    <div className="absolute top-8" style={{ left: start }}>
      <Popover.Root open={open} onOpenChange={setOpen} modal={false}>
        <Popover.Trigger className="flex h-9 w-32 flex-col justify-center rounded-md border border-blue-700 bg-blue-600 px-2 py-1 text-left text-white text-xs shadow-sm">
          <span className="truncate font-semibold">{label}</span>
          <span>09:30</span>
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Positioner
            side="bottom"
            align="start"
            sideOffset={8}
            collisionPadding={0}
            collisionBoundary={boundary ?? undefined}
            sticky
            className="z-50"
          >
            <Popover.Popup data-popped-out>
              <VariantA4
                canEdit
                onCollapse={() => setOpen(false)}
                idPlacement="title"
                overflow={OVERFLOW_ACTIONS}
                name={LONG_NAME}
                location={LONG_LOCATION}
                width="min(22rem, var(--available-width))"
              />
            </Popover.Popup>
          </Popover.Positioner>
        </Popover.Portal>
      </Popover.Root>
    </div>
  );
}

function PoppedOutScroller({ label, viewport }: { label: string; viewport: string }) {
  const [boundary, setBoundary] = React.useState<HTMLElement | null>(null);
  return (
    <li className="space-y-2">
      <p className="font-mono text-slate-500 text-xs">scroller={label}</p>
      <div
        ref={setBoundary}
        data-scroller
        className="overflow-x-auto rounded-sm border border-slate-300 bg-white"
        style={{ width: viewport }}
      >
        <div className="relative h-40 min-w-[1920px]">
          <div
            className="pointer-events-none absolute inset-0 grid grid-cols-[repeat(24,minmax(0,1fr))]"
            aria-hidden="true"
          >
            {Array.from({ length: 24 }, (_, hour) => (
              <div key={hour} className={`relative border-slate-200 border-l ${hour % 2 === 0 ? "bg-slate-50" : ""}`}>
                <span className="absolute top-1 left-1 text-[10px] text-slate-400">{hour}</span>
              </div>
            ))}
          </div>
          <PoppedOutChip label="Near left edge" start="2%" boundary={boundary} />
          <PoppedOutChip label="Near right edge" start="14%" boundary={boundary} />
        </div>
      </div>
    </li>
  );
}

function PoppedOutGrid() {
  return (
    <main className="min-h-screen bg-slate-100 px-8 py-10 text-slate-950">
      <div className="mx-auto max-w-5xl">
        <header className="mb-7">
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card — popped out</p>
          <h1 className="mt-1 font-semibold text-2xl">Does portalling the card out fix the viewport problem?</h1>
          <p className="mt-2 max-w-3xl text-slate-600 text-sm">
            Click a chip. The card is portalled to the body and anchored to the chip, with the scroller as the collision
            boundary, so it should shift or flip to stay inside the visible window instead of being clipped by it.
            Scroll a box sideways with the card open to see anchor tracking and <code>sticky</code>.
          </p>
          <p className="mt-2 max-w-3xl font-medium text-emerald-800 text-sm">
            Width needs no breakpoint. With <code>collisionPadding</code> at 0 the card is sized{" "}
            <code className="rounded bg-slate-200 px-1 font-mono text-xs">min(22rem, var(--available-width))</code>, so
            a window narrower than 22rem gives it the whole window and anything wider gives it its preferred 22rem.
            Small viewport is not a magic number here, it is just narrower than the card wants to be.
          </p>
        </header>
        <ul className="space-y-8">
          <PoppedOutScroller label="320px (small phone) — card fills the window" viewport="320px" />
          <PoppedOutScroller label="480px — card takes its preferred 22rem" viewport="480px" />
        </ul>
      </div>
    </main>
  );
}

export const PoppedOutA4: Story = {
  render: () => <PoppedOutGrid />,
};
