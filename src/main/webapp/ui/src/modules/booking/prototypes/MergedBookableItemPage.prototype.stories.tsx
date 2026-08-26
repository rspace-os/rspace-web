// PROTOTYPE — throwaway. Two surviving answers to: what does a merged
// bookable-item view+edit page look like?
//
// A (mode switch) and D (spotlight header + tabs) are what is left. B (inline
// section edit) and C (live form + sticky save bar) were cut; D kept B's
// SectionCard, so that fragment survives its variant.
//
// Today there are two pages:
//   /booking/bookable-items/$globalId          BookableItemPage      (read-only)
//   /booking/config/bookable-items/$id/edit    EditBookableItemPage  (form)
//
// Routing conclusion, recorded here rather than implemented (see NOTES at the
// bottom of this file): the merged page lives at the $globalId URL and the
// $id/edit route dies.
//
// Not wired to fetching, formisch, i18n or the router. Local state only, real
// UI components, realistic data shapes. Storybook's sidebar is the variant
// switcher.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Tabs } from "@base-ui/react/tabs";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarClockIcon, CalendarRangeIcon, ExternalLinkIcon, PencilIcon, SearchIcon } from "lucide-react";
import { useState } from "react";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardFooter, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Field, FieldDescription, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { InputGroup, InputGroupAddon, InputGroupText } from "@/modules/common/ui/input-group";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Switch } from "@/modules/common/ui/switch";
import { Heading } from "@/modules/common/ui/typography";

/* ------------------------------------------------------------------ data -- */

type Config = {
  id: number;
  globalId: string;
  name: string;
  enabled: boolean;
  timezone: string;
  slotGranularityMinutes: number;
  openingStart: string;
  openingEnd: string;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  maxBookingDurationMinutes: number;
  allowDoubleBooking: boolean;
  updatedAt: string;
};

const SAVED: Config = {
  id: 7,
  globalId: "IN123",
  name: "Confocal microscope (Zeiss LSM 980)",
  enabled: true,
  timezone: "Europe/London",
  slotGranularityMinutes: 15,
  openingStart: "08:00",
  openingEnd: "18:00",
  bufferBeforeMinutes: 15,
  bufferAfterMinutes: 15,
  maxBookingDurationMinutes: 240,
  allowDoubleBooking: false,
  updatedAt: "2026-08-14T09:32:00Z",
};

type BookingEvent = { id: string; who: string; when: string; state: string };

const UPCOMING: BookingEvent[] = [
  { id: "41", who: "Priya Raman", when: "Tue 25 Aug, 09:00 – 11:00", state: "Confirmed" },
  { id: "42", who: "Tomas Lindqvist", when: "Tue 25 Aug, 14:00 – 15:30", state: "Confirmed" },
  { id: "43", who: "Facilities", when: "Wed 26 Aug, 07:00 – 09:00", state: "Maintenance" },
];
const PAST: BookingEvent[] = [{ id: "38", who: "Priya Raman", when: "Mon 18 Aug, 09:00 – 10:00", state: "Confirmed" }];

// Same shape as AuditLogViews.prototype's BOOKABLE_EVENTS, restated here rather
// than imported: every named export of a *.stories.tsx is indexed as a story,
// so these two prototypes cannot share module scope.
type AuditEvent = {
  id: string;
  timestamp: string;
  actor: string;
  username: string;
  action: string;
  description: string;
  values: Array<[string, string]>;
};

const AUDIT_EVENTS: AuditEvent[] = [
  {
    id: "evt-7842",
    timestamp: "25 Aug 2026, 10:42:18",
    actor: "Morgan Ellis",
    username: "morgan.ellis",
    action: "WRITE",
    description: "Updated booking configuration IN123",
    values: [
      ["Enabled", "true"],
      ["Time zone", "Europe/London"],
      ["Maximum duration", "240 minutes"],
    ],
  },
  {
    id: "evt-7791",
    timestamp: "22 Aug 2026, 16:08:51",
    actor: "Morgan Ellis",
    username: "morgan.ellis",
    action: "WRITE",
    description: "Updated scheduling policy for IN123",
    values: [
      ["Opening hours", "08:00–18:00"],
      ["Slot granularity", "15 minutes"],
      ["Buffer", "15 minutes"],
    ],
  },
  {
    id: "evt-7410",
    timestamp: "14 Aug 2026, 09:32:04",
    actor: "System Administrator",
    username: "sysadmin1",
    action: "CREATE",
    description: "Created booking configuration IN123",
    values: [
      ["Enabled", "false"],
      ["Time zone", "Europe/London"],
    ],
  },
];

const DEFAULT_RANGE = { from: "2026-08-01", to: "2026-08-25" };

const DATE_PRESETS = [
  { id: "7d", label: "Last 7 days", from: "2026-08-18" },
  { id: "30d", label: "Last 30 days", from: "2026-07-26" },
  { id: "90d", label: "Last 90 days", from: "2026-05-27" },
];

function changedKeys(draft: Config, baseline: Config): Array<keyof Config> {
  return (Object.keys(baseline) as Array<keyof Config>).filter((key) => draft[key] !== baseline[key]);
}

function facts(config: Config): Array<[string, string]> {
  return [
    ["Time zone", config.timezone],
    ["Opening hours", `${config.openingStart}–${config.openingEnd}`],
    ["Slot granularity", `${config.slotGranularityMinutes} minutes`],
    [
      "Maximum duration",
      config.maxBookingDurationMinutes === 0 ? "Unlimited" : `${config.maxBookingDurationMinutes} minutes`,
    ],
    ["Buffer before", `${config.bufferBeforeMinutes} minutes`],
    ["Buffer after", `${config.bufferAfterMinutes} minutes`],
    ["Double booking", config.allowDoubleBooking ? "Allowed" : "Not allowed"],
    ["Last updated", "14 Aug 2026, 10:32"],
  ];
}

/* -------------------------------------------------- collection configs -- */
// Every table on this page is a TableList over a resolved collection config,
// so search, empty state, and the card presentation on narrow containers all
// come from the same machinery the real Booking pages use.

// Two booking configs rather than one: TableList titles itself from
// `labels.pluralKey`, and Upcoming and Past need different titles.
function bookingConfig(slug: string, pluralKey: string) {
  return resolveCollectionConfig<BookingEvent>({
    slug,
    idField: "id",
    labels: { singularKey: "Booking", pluralKey },
    useAsTitle: "when",
    defaultColumns: ["when", "who", "state"],
    listSearchableFields: ["who", "state"],
    fields: [
      { name: "id", type: "text", labelKey: "Booking ID", list: false },
      { name: "when", type: "text", labelKey: "When", list: { width: 280, minWidth: 200 } },
      { name: "who", type: "text", labelKey: "Booked by", list: { width: 220, minWidth: 150 } },
      {
        name: "state",
        type: "text",
        labelKey: "Status",
        list: {
          width: 160,
          minWidth: 120,
          renderCell: ({ row }) => (
            <Badge variant={row.state === "Maintenance" ? "secondary" : "outline"}>{row.state}</Badge>
          ),
        },
      },
    ],
  });
}

const upcomingBookingConfig = bookingConfig("upcoming-bookings", "Upcoming bookings");
const pastBookingConfig = bookingConfig("past-bookings", "Past bookings");

const auditEventConfig = resolveCollectionConfig<AuditEvent>({
  slug: "audit-events",
  idField: "id",
  labels: { singularKey: "Audit event", pluralKey: "Audit events" },
  useAsTitle: "timestamp",
  defaultColumns: ["timestamp", "actor", "action", "values"],
  listSearchableFields: ["actor", "username", "action", "description"],
  fields: [
    { name: "id", type: "text", labelKey: "Event ID", list: false },
    { name: "timestamp", type: "text", labelKey: "Time", list: { width: 190, minWidth: 170 } },
    {
      name: "actor",
      type: "text",
      labelKey: "Changed by",
      list: {
        width: 190,
        minWidth: 160,
        dependencies: ["username"],
        renderCell: ({ row }) => (
          <span>
            <span className="block font-medium">{row.actor}</span>
            <span className="text-xs text-muted-foreground">{row.username}</span>
          </span>
        ),
      },
    },
    { name: "username", type: "text", labelKey: "Username" },
    {
      name: "action",
      type: "text",
      labelKey: "Action",
      list: {
        width: 300,
        minWidth: 220,
        dependencies: ["description"],
        renderCell: ({ row }) => (
          <span>
            <Badge variant="outline">{row.action}</Badge>
            <span className="mt-2 block whitespace-normal text-muted-foreground">{row.description}</span>
          </span>
        ),
      },
    },
    { name: "description", type: "text", labelKey: "Description" },
    {
      name: "values",
      type: "text",
      labelKey: "Recorded values",
      list: {
        width: 310,
        minWidth: 240,
        card: { fullWidth: true },
        renderCell: ({ row }) => <AuditValues values={row.values} />,
      },
    },
  ],
});

/* ------------------------------------------------------ shared fragments -- */
// Deliberately small: the header identity block, the rules read-out, the rules
// inputs, and the events lists. Everything about page *layout* is per-variant.

function ItemIdentity({ config, size }: { config: Config; size?: "sm" }) {
  return (
    <InventoryItem
      name={config.name}
      globalId={config.globalId}
      href={`/globalId/${config.globalId}`}
      idLinkLabel={`Open ${config.name} in Inventory`}
      size={size}
    />
  );
}

function RulesReadOut({ config }: { config: Config }) {
  return (
    <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-[max-content_1fr]">
      {facts(config).map(([label, value]) => (
        <div className="grid gap-1 sm:contents" key={label}>
          <dt className="font-medium">{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function RulesInputs({
  draft,
  onChange,
  showEnabled = false,
}: {
  draft: Config;
  onChange: (patch: Partial<Config>) => void;
  showEnabled?: boolean;
}) {
  return (
    <div className="space-y-6">
      {showEnabled ? (
        <Field orientation="horizontal">
          <Switch
            id="p-enabled"
            checked={draft.enabled}
            onCheckedChange={(checked) => onChange({ enabled: checked })}
          />
          <FieldLabel htmlFor="p-enabled">Bookable</FieldLabel>
        </Field>
      ) : null}
      <Field>
        <FieldLabel htmlFor="p-timezone">Time zone</FieldLabel>
        <select
          id="p-timezone"
          className="h-9 w-full rounded-sm bg-input/50 px-3 text-sm"
          value={draft.timezone}
          onChange={(event) => onChange({ timezone: event.currentTarget.value })}
        >
          {["Europe/London", "Europe/Berlin", "UTC", "America/New_York"].map((zone) => (
            <option key={zone} value={zone}>
              {zone}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="p-open-start">Opens</FieldLabel>
          <Input
            id="p-open-start"
            type="time"
            step={60}
            value={draft.openingStart}
            onChange={(event) => onChange({ openingStart: event.currentTarget.value })}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="p-open-end">Closes</FieldLabel>
          <Input
            id="p-open-end"
            type="time"
            step={60}
            value={draft.openingEnd}
            onChange={(event) => onChange({ openingEnd: event.currentTarget.value })}
          />
        </Field>
      </div>
      <Field>
        <FieldLabel htmlFor="p-granularity">Slot granularity</FieldLabel>
        <select
          id="p-granularity"
          className="h-9 w-full rounded-sm bg-input/50 px-3 text-sm"
          value={draft.slotGranularityMinutes}
          onChange={(event) => onChange({ slotGranularityMinutes: Number(event.currentTarget.value) })}
        >
          {[1, 5, 15].map((minutes) => (
            <option key={minutes} value={minutes}>
              {minutes} minutes
            </option>
          ))}
        </select>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="p-buffer">Buffer</FieldLabel>
          <Input
            id="p-buffer"
            type="number"
            min={0}
            value={draft.bufferBeforeMinutes}
            onChange={(event) =>
              onChange({
                bufferBeforeMinutes: event.currentTarget.valueAsNumber,
                bufferAfterMinutes: event.currentTarget.valueAsNumber,
              })
            }
          />
          <FieldDescription>Applied before and after every booking.</FieldDescription>
        </Field>
        <Field>
          <FieldLabel htmlFor="p-max">Maximum duration</FieldLabel>
          <Input
            id="p-max"
            type="number"
            min={0}
            step={draft.slotGranularityMinutes}
            value={draft.maxBookingDurationMinutes}
            onChange={(event) => onChange({ maxBookingDurationMinutes: event.currentTarget.valueAsNumber })}
          />
          <FieldDescription>0 means unlimited.</FieldDescription>
        </Field>
      </div>
      <Field orientation="horizontal">
        <Checkbox
          id="p-double"
          checked={draft.allowDoubleBooking}
          onCheckedChange={(checked) => onChange({ allowDoubleBooking: checked === true })}
        />
        <FieldLabel htmlFor="p-double">Allow double booking</FieldLabel>
      </Field>
    </div>
  );
}

// `queryString: false` on every table here: two or more TableLists share this
// page, and they would otherwise all read and write the same URL keys.
function BookingsTable({ config, rows }: { config: typeof upcomingBookingConfig; rows: readonly BookingEvent[] }) {
  const table = useTableList({
    config,
    dataSource: { type: "client", rows },
    features: { pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });

  return (
    <TableList
      {...table.tableProps}
      presentations={{ table: "wide", cards: "narrow" }}
      emptyDescription="Nothing scheduled for this item."
      variant="transparent"
    />
  );
}

function EventsSections() {
  return (
    <>
      <BookingsTable config={upcomingBookingConfig} rows={UPCOMING} />
      <BookingsTable config={pastBookingConfig} rows={PAST} />
    </>
  );
}

function StateReadout({ text }: { text: string }) {
  return (
    <output className="block rounded-sm border border-dashed bg-muted/30 px-3 py-2 font-mono text-xs text-muted-foreground">
      {text}
    </output>
  );
}

/* ----------------------------------------------------------- variant A ---- */
// "Mode switch". The page has one Edit affordance in the header. Editing
// replaces the whole body: rules become a form, the events lists are hidden
// behind a reminder that they are still there. Closest to today's two pages,
// but one URL and one fetch. In the real page the mode is `?edit=1`, so it is
// linkable, back-button-able, and reload-stable.

function VariantA({ canEdit }: { canEdit: boolean }) {
  const [saved, setSaved] = useState(SAVED);
  const [draft, setDraft] = useState<Config | null>(null);
  const editing = draft !== null;

  return (
    <main className="mx-auto max-w-4xl space-y-8 p-4 sm:p-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-3">
          <Heading level={2} as="h1">
            {editing ? "Editing bookable item" : "Bookable item"}
          </Heading>
          <ItemIdentity config={saved} />
        </div>
        <div className="flex items-center gap-3">
          <Badge variant={saved.enabled ? "default" : "secondary"}>{saved.enabled ? "Enabled" : "Disabled"}</Badge>
          {canEdit && !editing ? (
            <Button type="button" variant="outline" onClick={() => setDraft(saved)}>
              <PencilIcon aria-hidden="true" />
              Edit
            </Button>
          ) : null}
        </div>
      </header>

      {editing ? (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Booking rules</CardTitle>
            </CardHeader>
            <CardContent>
              <RulesInputs draft={draft} onChange={(patch) => setDraft({ ...draft, ...patch })} showEnabled />
            </CardContent>
            <CardFooter className="gap-3">
              <Button
                type="button"
                onClick={() => {
                  setSaved(draft);
                  setDraft(null);
                }}
              >
                Save changes
              </Button>
              <Button type="button" variant="ghost" onClick={() => setDraft(null)}>
                Cancel
              </Button>
            </CardFooter>
          </Card>
          <p className="text-sm text-muted-foreground">
            Bookings are hidden while you edit. Save or cancel to see them again.
          </p>
        </>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Booking rules</CardTitle>
            </CardHeader>
            <CardContent>
              <RulesReadOut config={saved} />
            </CardContent>
          </Card>
          <EventsSections />
        </>
      )}

      <StateReadout
        text={`variant=A mode=${editing ? "edit (?edit=1)" : "view"} canEdit=${canEdit} dirty=${
          editing ? changedKeys(draft, saved).length : 0
        }`}
      />
    </main>
  );
}

/* ----------------------------------------------------- shared: section -- */
// SectionCard came from the deleted variant B (inline per-section editing).
// Variant D kept that editing model, so the card outlived its variant.

function SectionCard({
  title,
  canEdit,
  children,
  form,
  onSave,
  onCancel,
  className,
}: {
  title: string;
  canEdit: boolean;
  children: React.ReactNode;
  form: React.ReactNode;
  onSave: () => void;
  onCancel: () => void;
  /** Variant D passes `rounded-sm` so the card matches its squared-off surfaces. */
  className?: string;
}) {
  const [editing, setEditing] = useState(false);
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {canEdit && !editing ? (
          <CardAction>
            <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(true)}>
              <PencilIcon aria-hidden="true" />
              Edit
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>{editing ? form : children}</CardContent>
      {editing ? (
        <CardFooter className="gap-3">
          <Button
            type="button"
            size="sm"
            onClick={() => {
              onSave();
              setEditing(false);
            }}
          >
            Save
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => {
              onCancel();
              setEditing(false);
            }}
          >
            Cancel
          </Button>
        </CardFooter>
      ) : null}
    </Card>
  );
}

/* ----------------------------------------------------------- variant D ---- */
// "Spotlight + tabs". Takes the A4 spotlight header from the AuditLogViews
// prototype and welds a tab strip to the bottom of it: the item is the
// headline, and Details / Audit log are siblings under it rather than two
// separate destinations. Editing reuses B's per-section cards, so the tab
// strip is the only thing this variant is really proposing.
//
// The audit tab keeps A4's period preset row above a TableList, the same
// shape AuditLogViews settled on.

// No Edit button here: editing is owned by the section cards below, the same
// way variant B does it, so a second header-level Edit would have nothing to
// drive.
function SpotlightHeader({ config }: { config: Config }) {
  return (
    <section className="rounded-sm border bg-background shadow-md">
      <div className="flex flex-wrap items-center gap-4 p-5">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-sm bg-primary text-primary-foreground">
          <CalendarClockIcon aria-hidden="true" className="size-6" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Bookable item</p>
          <Heading level={4} as="h1" className="mt-0.5 truncate">
            {config.name}
          </Heading>
          <p className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <Badge
              variant="outline"
              className="font-mono"
              render={
                <a href={`/globalId/${config.globalId}`} aria-label={`Open ${config.name} in Inventory`}>
                  <ExternalLinkIcon aria-hidden="true" />
                  {config.globalId}
                </a>
              }
            />
            {config.timezone}
          </p>
        </div>
        <Badge variant={config.enabled ? "default" : "secondary"} className="shrink-0">
          {config.enabled ? "Enabled" : "Disabled"}
        </Badge>
      </div>
    </section>
  );
}

function PageTab({ value, children }: { value: string; children: React.ReactNode }) {
  return (
    <Tabs.Tab
      value={value}
      className="-mb-px cursor-default border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground transition-colors outline-none select-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground"
    >
      {children}
    </Tabs.Tab>
  );
}

// Standalone strip rather than a row welded to the spotlight, so the tabs read
// as page-level navigation and the header stays a self-contained card. It owns
// its own baseline; PageTab's `-mb-px` lifts the active underline onto it.
function PageTabs({ tabs }: { tabs: Array<{ value: string; label: string }> }) {
  return (
    <Tabs.List className="flex flex-wrap border-b">
      {tabs.map(({ value, label }) => (
        <PageTab key={value} value={value}>
          {label}
        </PageTab>
      ))}
    </Tabs.List>
  );
}

const PAGE_TABS = [
  { value: "details", label: "Details" },
  { value: "audit", label: "Audit log" },
];

function AuditValues({ values }: { values: string[][] }) {
  return (
    <dl className="grid min-w-52 grid-cols-[max-content_1fr] gap-x-3 gap-y-1 font-mono text-xs">
      {values.map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="text-muted-foreground">{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function AuditTabContent() {
  const [range, setRange] = useState(DEFAULT_RANGE);
  const presetId = DATE_PRESETS.find(({ from }) => from === range.from && range.to === DEFAULT_RANGE.to)?.id;
  const auditTable = useTableList({
    config: auditEventConfig,
    dataSource: { type: "client", rows: AUDIT_EVENTS },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-1 text-sm font-medium">Period</span>
        {DATE_PRESETS.map((preset) => (
          <Button
            key={preset.id}
            type="button"
            size="sm"
            variant={presetId === preset.id ? "default" : "outline"}
            aria-pressed={presetId === preset.id}
            onClick={() => setRange({ from: preset.from, to: DEFAULT_RANGE.to })}
          >
            {preset.label}
          </Button>
        ))}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <InputGroup className="w-auto">
            <InputGroupAddon>
              <CalendarRangeIcon aria-hidden="true" />
            </InputGroupAddon>
            <input
              aria-label="From date"
              className="h-9 bg-transparent text-sm outline-none"
              type="date"
              value={range.from}
              onChange={(event) => setRange({ ...range, from: event.currentTarget.value })}
            />
            <InputGroupText aria-hidden="true" className="px-2">
              –
            </InputGroupText>
            <input
              aria-label="To date"
              className="h-9 bg-transparent pr-3 text-sm outline-none"
              type="date"
              value={range.to}
              onChange={(event) => setRange({ ...range, to: event.currentTarget.value })}
            />
          </InputGroup>
          <Button type="button">
            <SearchIcon aria-hidden="true" />
            Load snapshot
          </Button>
        </div>
      </div>

      <TableList
        {...auditTable.tableProps}
        presentations={{ table: "wide", cards: "narrow" }}
        createAction={
          <p className="text-xs text-muted-foreground">Results as of 25 Aug 2026, 10:45:00 UTC · newest first</p>
        }
        emptyDescription="Try a different period."
        variant="transparent"
      />
    </div>
  );
}

function VariantD({ canEdit }: { canEdit: boolean }) {
  const [saved, setSaved] = useState(SAVED);
  const [draft, setDraft] = useState(SAVED);
  const [tab, setTab] = useState("details");

  return (
    <main className="mx-auto max-w-5xl p-4 sm:p-8">
      <Tabs.Root value={tab} onValueChange={(value) => setTab(String(value))} className="space-y-6">
        <SpotlightHeader config={saved} />
        <PageTabs tabs={PAGE_TABS} />

        <Tabs.Panel value="details" className="space-y-8 outline-none">
          <SectionCard
            title="Booking rules"
            canEdit={canEdit}
            className="rounded-sm"
            form={<RulesInputs draft={draft} onChange={(patch) => setDraft({ ...draft, ...patch })} showEnabled />}
            onSave={() => setSaved(draft)}
            onCancel={() => setDraft(saved)}
          >
            <RulesReadOut config={saved} />
          </SectionCard>
          <EventsSections />
        </Tabs.Panel>

        <Tabs.Panel value="audit" className="outline-none">
          <AuditTabContent />
        </Tabs.Panel>
      </Tabs.Root>

      <div className="mt-8">
        <StateReadout
          text={`variant=D tab=${tab} (?tab=${tab}) canEdit=${canEdit} pendingInOpenSection=${
            changedKeys(draft, saved).length
          }`}
        />
      </div>
    </main>
  );
}

/* -------------------------------------------------------------- stories -- */

function AllVariants({ canEdit }: { canEdit: boolean }) {
  return (
    <div className="divide-y">
      {(
        [
          ["A — mode switch (?edit=1)", VariantA],
          ["D — spotlight header + Details / Audit log tabs", VariantD],
        ] as const
      ).map(([label, Variant]) => (
        <section key={label}>
          <p className="bg-muted px-4 py-2 font-mono text-xs uppercase tracking-wide">{label}</p>
          <Variant canEdit={canEdit} />
        </section>
      ))}
    </div>
  );
}

const meta = {
  title: "Booking/Prototypes/MergedBookableItemPage",
  component: AllVariants,
  parameters: { layout: "fullscreen" },
  args: { canEdit: true },
  argTypes: { canEdit: { control: "boolean", description: "Simulates currentUser.hasSysAdminRole" } },
  // TableList translates its own toolbar, so these stories need the catalog.
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof AllVariants>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompareVariants: Story = {};
export const A_ModeSwitch: Story = { render: ({ canEdit }) => <VariantA canEdit={canEdit} /> };
export const D_SpotlightTabs: Story = { render: ({ canEdit }) => <VariantD canEdit={canEdit} /> };
export const ReadOnlyUser: Story = { args: { canEdit: false } };

/* ---------------------------------------------------------------- NOTES -- */
//
// Routing (same for all three variants, not prototyped here):
//   * Merged page keeps `/booking/bookable-items/$globalId`. It is the URL that
//     is already linked from the items table, the add-page conflict message and
//     the calendar, and it is the one a non-admin can reach.
//   * `createEditBookableItemRoute` and `EditBookableItemPage` are deleted.
//     `/booking/config/bookable-items/$id/edit` can 301 to the globalId URL if
//     anything has bookmarked it; otherwise just drop it.
//   * PATCH needs the numeric configuration id. It is already in the payload
//     that `fetchBookingConfigurationByTarget` returns (`data.id`), so the
//     merged page needs no second request and no second query key.
//   * EditBookableItemPage's on-success `navigate` to the view page collapses
//     to a query invalidation. Its `target === null` fallback branch also goes,
//     because you cannot reach a globalId URL for a configuration with no
//     target.
//   * AddBookableItemPage keeps `bookingConfigurationFields` and
//     `SchedulingSettingsFields`. Whichever variant wins must keep using those
//     two, or add and edit drift apart. This prototype uses hand-rolled inputs
//     only to avoid dragging formisch in.
//
// What each surviving variant is really betting on:
//   A  Editing is rare and deliberate. Cheapest to build: it is the current two
//      pages with one fetch and one URL. Cost is that the bookings disappear at
//      exactly the moment you want to check them.
//   D  The item has more than one thing worth reading about it, so the page is
//      a tabbed container rather than one scroll. Gives the audit trail a home
//      without a second URL segment, and leaves room for the next tab
//      (blockouts, permissions). Cost is that the tab becomes router state —
//      `?tab=audit`, restored on reload and on back — and the audit query only
//      fires when its tab is first opened, which is a second loading state to
//      design.
//
//      Editing is B's model, kept: each section card owns its own dirty scope,
//      so there is no page-level unsaved-changes guard to write.
//
//      MEASURED, not assumed: `Tabs.Panel` defaults to `keepMounted={false}`,
//      so only one panel is in the DOM at a time. Open Edit on Booking rules,
//      change a field, switch to Audit log, switch back: SectionCard's internal
//      `editing` flag is gone (the card is read-only again, showing the saved
//      value) while `draft` survives in VariantD, so the readout still reports
//      `pendingInOpenSection=1`. The edit is invisible but not discarded, which
//      is the worst of both. Whoever picks D must choose one of:
//        * `keepMounted` on the Details panel, so open forms survive the trip;
//        * hoist `editing` out of SectionCard, so the page owns edit state;
//        * block the tab switch while a section is dirty, same guard A and C
//          already need for navigation.
//
// Open questions for whoever picks:
//   1. Does `enabled` stay a badge, or become a header switch that saves on its
//      own? The deleted variant B had the switch, and it was the only shape
//      that took an item offline without a full save round-trip.
//   2. A needs an unsaved-changes guard on navigation. D does not, because its
//      dirty scope is one section card — but see the tab round trip above.
//   3. Both tables in D's Details tab are TableLists, so each carries its own
//      search box. On a page with Upcoming and Past that is two search boxes
//      for one concept; consider one table with a status filter instead.
