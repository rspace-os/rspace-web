// PROTOTYPE ONLY: compare three ways to fit upcoming bookings into a narrow dashboard widget.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useEffect, useState } from "react";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableListCardGrid } from "@/modules/common/table-list/components/TableListCardView";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card } from "@/modules/common/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/modules/common/ui/dialog";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";

const bookings = [
  {
    id: 41,
    day: "Today",
    time: "14:00–16:00",
    name: "Confocal microscope",
    purpose: "Live-cell imaging",
    location: "Imaging suite · Room 204",
  },
  {
    id: 42,
    day: "Today",
    time: "16:30–17:00",
    name: "Flow cytometer",
    purpose: "Panel validation",
    location: "Core facility · Room 102",
  },
  {
    id: 43,
    day: "Tomorrow",
    time: "09:00–10:30",
    name: "High-resolution liquid chromatography mass spectrometer",
    purpose: "Metabolomics batch 07 · repeat analysis",
    location: "Analytical laboratory · Room 310",
  },
  {
    id: 44,
    day: "Tomorrow",
    time: "11:00–12:00",
    name: "Plate reader",
    purpose: "Assay validation",
    location: "Core facility · Room 102",
  },
  {
    id: 45,
    day: "Tomorrow",
    time: "15:00–16:00",
    name: "Unavailable item",
    purpose: "Scheduled analysis",
    location: "",
    unavailable: true,
  },
  {
    id: 46,
    day: "26 Sep",
    time: "08:00–09:00",
    name: "Centrifuge",
    purpose: "Sample preparation",
    location: "Wet lab · Room 205",
  },
  {
    id: 47,
    day: "26 Sep",
    time: "10:00–11:30",
    name: "Spectrometer",
    purpose: "Quality control",
    location: "Analytical laboratory · Room 310",
  },
  {
    id: 48,
    day: "27 Sep",
    time: "22:00–02:00 +1d",
    name: "Cell culture room 2",
    purpose: "Overnight observation",
    location: "Cell culture · Room 108",
  },
];
type Booking = (typeof bookings)[number];
const variants = ["Compact table", "Day agenda", "Expandable rows"] as const;
type Variant = (typeof variants)[number];

function CompactTable({ rows, onOpen }: { rows: Booking[]; onOpen: (row: Booking) => void }) {
  return (
    <table className="w-full table-fixed text-left text-sm">
      <caption className="sr-only">Upcoming bookings</caption>
      <thead className="sticky top-0 z-10 bg-muted text-xs text-muted-foreground">
        <tr>
          <th className="w-28 px-3 py-2 font-medium">When</th>
          <th className="px-3 py-2 font-medium">Booking</th>
        </tr>
      </thead>
      <tbody className="divide-y">
        {rows.map((row) => (
          <tr key={row.id} className="align-top hover:bg-muted/40">
            <td className="px-3 py-2">
              <div className="text-xs text-muted-foreground">{row.day}</div>
              <div className="whitespace-nowrap text-xs tabular-nums">{row.time}</div>
            </td>
            <td className="min-w-0 px-3 py-2">
              <button
                type="button"
                className="block w-full truncate text-left font-medium text-primary underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-ring"
                title={row.name}
                onClick={() => onOpen(row)}
              >
                {row.name}
              </button>
              <div className="truncate text-xs text-muted-foreground" title={row.purpose}>
                {row.purpose}
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DayAgenda({ rows, onOpen }: { rows: Booking[]; onOpen: (row: Booking) => void }) {
  return (
    <div>
      {[...new Set(rows.map((row) => row.day))].map((day) => (
        <section key={day} aria-label={day}>
          <h3 className="sticky top-0 z-10 border-b bg-muted px-3 py-1.5 text-xs font-semibold">{day}</h3>
          <ul className="divide-y">
            {rows
              .filter((row) => row.day === day)
              .map((row) => (
                <li key={row.id}>
                  <details className="group">
                    <summary className="grid w-full cursor-pointer list-none grid-cols-[6.5rem_minmax(0,1fr)_1rem] items-start gap-2 px-3 py-2 text-left hover:bg-muted/40 focus-visible:outline focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
                      <span className="pt-0.5 text-xs tabular-nums">{row.time}</span>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium" title={row.name}>
                          {row.name}
                        </span>
                        <span className="block truncate text-xs text-muted-foreground" title={row.purpose}>
                          {row.purpose}
                        </span>
                      </span>
                      <ChevronDownIcon
                        aria-hidden
                        className="mt-0.5 size-4 text-muted-foreground group-open:rotate-180"
                      />
                    </summary>
                    <div className="px-3 py-2 [&_[data-slot=table-list-card-view]]:p-0 [&_article]:border-0 [&_article]:bg-transparent [&_article]:shadow-none [&_article>header]:border-0 [&_article>header]:p-0 [&_article>dl]:p-0 [&_article>dl]:my-3 [&_article>footer]:border-0 [&_article>footer]:bg-transparent [&_article>footer]:p-0">
                      <TableListCardGrid
                        label="Booking summary"
                        items={[
                          {
                            id: String(row.id),
                            title: row.unavailable ? (
                              <UnknownItem size="xs" />
                            ) : (
                              <InventoryItem
                                name={row.name}
                                globalId={`IN${row.id}`}
                                idPlacement="title"
                                className="p-0"
                              >
                                <span className="text-xs text-muted-foreground">{row.location}</span>
                              </InventoryItem>
                            ),
                            fields: [
                              { id: "day", label: "Day", value: row.day },
                              { id: "time", label: "Time", value: row.time },
                              { id: "purpose", label: "Purpose", value: row.purpose, fullWidth: true },
                            ],
                            actions: (
                              <Button variant="outline" size="sm" onClick={() => onOpen(row)}>
                                Booking details
                              </Button>
                            ),
                          },
                        ]}
                      />
                    </div>
                  </details>
                </li>
              ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function ExpandableRows({ rows, onOpen }: { rows: Booking[]; onOpen: (row: Booking) => void }) {
  return (
    <div className="divide-y">
      {rows.map((row) => (
        <details key={row.id} className="group">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 hover:bg-muted/40 focus-visible:outline focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium" title={row.name}>
                {row.name}
              </span>
              <span className="block text-xs text-muted-foreground">
                {row.day} · <span className="tabular-nums">{row.time}</span>
              </span>
            </span>
            <ChevronDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground group-open:rotate-180" />
          </summary>
          <div className="space-y-2 bg-muted/30 px-3 pb-3 pt-1 text-sm">
            <p className="break-words font-medium">{row.name}</p>
            <p>{row.purpose}</p>
            <p className="text-xs text-muted-foreground">
              {row.unavailable
                ? "This item no longer exists, or you no longer have permission to view this item."
                : row.location}
            </p>
            <Button variant="outline" size="sm" onClick={() => onOpen(row)}>
              Booking details
            </Button>
          </div>
        </details>
      ))}
    </div>
  );
}

function UpcomingBookingsCompactPrototype({ initialVariant = "Compact table" }: { initialVariant?: Variant }) {
  const [variant, setVariant] = useState<Variant>(() => {
    const saved = new URLSearchParams(window.location.search).get("variant");
    return variants.find((value) => value === saved) ?? initialVariant;
  });
  const [width, setWidth] = useState(400);
  const [height, setHeight] = useState(340);
  const [empty, setEmpty] = useState(false);
  const [selected, setSelected] = useState<Booking | null>(null);
  const [showAll, setShowAll] = useState(false);
  const rows = empty ? [] : bookings;
  const changeVariant = (next: Variant) => {
    setVariant(next);
    const url = new URL(window.location.href);
    url.searchParams.set("variant", next);
    window.history.replaceState(null, "", url);
  };
  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if (
        !(event.target instanceof HTMLElement) ||
        event.target.closest("input, select, textarea, button, summary, [contenteditable=true], [role=dialog]")
      )
        return;
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      const index = variants.indexOf(variant);
      changeVariant(variants[(index + (event.key === "ArrowRight" ? 1 : -1) + variants.length) % variants.length]);
    };
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, [variant]);

  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <main className="min-h-screen space-y-6 bg-background p-4 pb-24 text-foreground sm:p-6 sm:pb-24">
        <header className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">Booking dashboard</h1>
          <Badge variant="secondary">Prototype</Badge>
        </header>
        <div className="flex flex-wrap items-center gap-4 rounded-sm border bg-muted/40 p-3 text-xs">
          <label className="flex items-center gap-2">
            Container width{" "}
            <select
              aria-label="Container width"
              className="rounded border bg-background p-1"
              value={width}
              onChange={(event) => setWidth(Number(event.target.value))}
            >
              {[320, 400, 560, 720].map((value) => (
                <option key={value} value={value}>
                  {value} px
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2">
            Container height{" "}
            <select
              aria-label="Container height"
              className="rounded border bg-background p-1"
              value={height}
              onChange={(event) => setHeight(Number(event.target.value))}
            >
              {[260, 340, 460].map((value) => (
                <option key={value} value={value}>
                  {value} px
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={empty} onChange={(event) => setEmpty(event.target.checked)} />
            Empty state
          </label>
          <span className="text-muted-foreground">
            {variant} · {rows.length} bookings · Europe/Berlin
          </span>
        </div>
        <section className="max-w-full space-y-3" style={{ width }} aria-label="Upcoming bookings prototype">
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-xl font-semibold tracking-tight">Upcoming bookings</h2>
            <button
              type="button"
              className="shrink-0 text-sm font-medium text-primary hover:underline"
              onClick={() => setShowAll(true)}
            >
              View all
            </button>
          </div>
          <Card className="gap-0 overflow-hidden rounded-sm py-0" style={{ height }}>
            <section
              className="min-h-0 flex-1 overflow-y-auto overscroll-contain"
              // biome-ignore lint/a11y/noNoninteractiveTabindex: keyboard scrolling needs a focus target.
              tabIndex={0}
              aria-label="Upcoming booking list"
            >
              {empty ? (
                <p className="p-6 text-center text-sm text-muted-foreground">No upcoming bookings.</p>
              ) : variant === "Compact table" ? (
                <CompactTable rows={rows} onOpen={setSelected} />
              ) : variant === "Day agenda" ? (
                <DayAgenda rows={rows} onOpen={setSelected} />
              ) : (
                <ExpandableRows rows={rows} onOpen={setSelected} />
              )}
            </section>
            <div className="flex shrink-0 justify-between border-t bg-background px-3 py-2 text-xs text-muted-foreground">
              <span>{rows.length} upcoming</span>
              <span>Europe/Berlin</span>
            </div>
          </Card>
        </section>
        <nav
          aria-label="Prototype layouts"
          className="fixed inset-x-3 bottom-4 z-20 mx-auto flex w-fit max-w-[calc(100%-1.5rem)] flex-wrap justify-center items-center gap-1 rounded-lg border bg-background p-2 shadow-lg"
        >
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label="Previous layout"
            onClick={() => changeVariant(variants[(variants.indexOf(variant) + variants.length - 1) % variants.length])}
          >
            <ChevronLeftIcon />
          </Button>
          {variants.map((value) => (
            <Button
              key={value}
              size="sm"
              variant={value === variant ? "secondary" : "ghost"}
              className={cn("px-2 text-xs", value === variant && "font-semibold")}
              aria-pressed={value === variant}
              onClick={() => changeVariant(value)}
            >
              {value}
            </Button>
          ))}
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label="Next layout"
            onClick={() => changeVariant(variants[(variants.indexOf(variant) + 1) % variants.length])}
          >
            <ChevronRightIcon />
          </Button>
        </nav>
        <Dialog
          open={selected !== null || showAll}
          onOpenChange={(open) => {
            if (!open) {
              setSelected(null);
              setShowAll(false);
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{showAll ? "All upcoming bookings" : selected?.name}</DialogTitle>
              <DialogDescription>
                {showAll ? "Prototype preview · Europe/Berlin" : `${selected?.day} · ${selected?.time} · Europe/Berlin`}
              </DialogDescription>
            </DialogHeader>
            {showAll ? (
              <div className="max-h-80 overflow-y-auto">
                <DayAgenda
                  rows={rows}
                  onOpen={(row) => {
                    setShowAll(false);
                    setSelected(row);
                  }}
                />
              </div>
            ) : (
              selected && (
                <div className="space-y-3 text-sm">
                  <p>{selected.purpose}</p>
                  <p className="text-muted-foreground">
                    {selected.unavailable
                      ? "This item no longer exists, or you no longer have permission to view this item."
                      : selected.location}
                  </p>
                  <p className="text-xs text-muted-foreground">Booking #{selected.id}</p>
                </div>
              )
            )}
          </DialogContent>
        </Dialog>
      </main>
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Compact Upcoming Bookings",
  component: UpcomingBookingsCompactPrototype,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof UpcomingBookingsCompactPrototype>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Compact: Story = { args: { initialVariant: "Compact table" } };
export const Agenda: Story = { args: { initialVariant: "Day agenda" } };
export const Expandable: Story = { args: { initialVariant: "Expandable rows" } };
