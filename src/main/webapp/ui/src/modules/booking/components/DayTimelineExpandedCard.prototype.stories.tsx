// The winning expanded DayTimeline card variant. B (reading rail) and C (agenda strip)
// lost and were removed; iterations on A live in prototypes/DayTimelineExpandedCardV2.
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ArrowLeft, ArrowRight, CalendarDays, Check, ChevronDown } from "lucide-react";
import * as React from "react";

const booking = {
  date: "Wednesday, 22 July",
  end: "11:00",
  item: "Confocal microscope",
  notes: "Confocal imaging of plate 4. Use the 63x oil objective and leave the chamber at 37 °C.",
  person: "Ada Lovelace",
  start: "09:30",
};

const variants = [{ key: "A", name: "Time first" }] as const;

type VariantKey = (typeof variants)[number]["key"];

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

function VariantA() {
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
          className="grid size-8 shrink-0 place-items-center rounded-full text-slate-600 hover:bg-white"
        >
          <ChevronDown className="size-4" />
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
          <Check className="size-3.5" /> You can edit
        </span>
        <div className="flex items-center">
          <PrototypeAction>Edit</PrototypeAction>
          <PrototypeAction>Open</PrototypeAction>
        </div>
      </footer>
    </article>
  );
}

function PrototypeSwitcher({ current, select }: { current: VariantKey; select: (variant: VariantKey) => void }) {
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
      <span className="min-w-40 px-2 text-center font-medium text-sm">
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
    </nav>
  );
}

function TimelineContext() {
  const [variant, setVariant] = React.useState<VariantKey>(() => {
    const requested = new URLSearchParams(window.location.search).get("variant");
    return variants.some(({ key }) => key === requested) ? (requested as VariantKey) : "A";
  });

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
          <p className="font-medium text-blue-700 text-sm">DayTimeline expanded card prototype</p>
          <h1 className="mt-1 font-semibold text-2xl">What deserves attention when a booking opens?</h1>
          <p className="mt-1 text-slate-600 text-sm">
            All variants show the same booking. Use the arrows or left and right keys to compare the hierarchy.
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
          <div className="relative flex min-h-[27rem] items-start justify-center bg-[linear-gradient(to_right,transparent_calc(100%/6_-_1px),rgb(226_232_240)_calc(100%/6_-_1px))] bg-[length:calc(100%/6)_100%] px-8 pt-14">
            <div className="absolute top-4 left-[8%] h-9 w-[13%] rounded-md border border-slate-400 bg-slate-200 px-2 py-1 text-slate-700 text-xs shadow-sm">
              <span className="block truncate font-semibold">Busy</span>
              <span>08:30</span>
            </div>
            <div className="absolute top-4 left-[72%] h-9 w-[18%] rounded-md border border-amber-600 bg-amber-100 px-2 py-1 text-amber-950 text-xs shadow-sm">
              <span className="block truncate font-semibold">Maintenance</span>
              <span>12:20</span>
            </div>
            {variant === "A" && <VariantA />}
          </div>
        </section>
      </div>
      <PrototypeSwitcher current={variant} select={select} />
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/DayTimeline expanded card",
  component: TimelineContext,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof TimelineContext>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompareVariants: Story = {};
