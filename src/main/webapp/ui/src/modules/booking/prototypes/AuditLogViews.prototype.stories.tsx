// PROTOTYPE — throwaway. Structurally different answers to: how should
// Booking users inspect security-grade audit records?
//
// Variants A / A2 / A3 / A4 share one page shell (heading + filters + table)
// and differ only in how the *filter selection* is displayed — that is the
// question this round is exploring, so the visual polish budget goes there.
// B and C are earlier whole-page answers, deliberately left unpolished.
//
// Not wired to fetching, i18n, authorization, or the router. Local state and
// mock data only. Production authorization remains a backend responsibility.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import {
  AlertTriangleIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  CalendarClockIcon,
  CalendarRangeIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  FileClockIcon,
  RefreshCwIcon,
  SearchIcon,
  ShieldCheckIcon,
  SlidersHorizontalIcon,
  UserRoundIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/modules/common/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { Field, FieldDescription, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { InputGroup, InputGroupAddon, InputGroupText } from "@/modules/common/ui/input-group";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Separator } from "@/modules/common/ui/separator";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";

type Scope = "bookable-item" | "user";
type Role = "user" | "administrator";
type ViewState = "populated" | "empty" | "loading" | "conflict" | "unavailable";
type Variant = "A" | "A2" | "A3" | "A4" | "B" | "C";
type FilterDisplay = Extract<Variant, "A" | "A2" | "A3" | "A4">;

type AuditEvent = {
  id: string;
  timestamp: string;
  actor: string;
  username: string;
  action: string;
  description: string;
  values: Array<[string, string]>;
};

type AuditPrototypeProps = {
  initialScope: Scope;
  role: Role;
  viewState: ViewState;
};

const VARIANTS: Array<{ id: Variant; label: string }> = [
  { id: "A", label: "Scope card" },
  { id: "A2", label: "Filter toolbar" },
  { id: "A3", label: "Query chips" },
  { id: "A4", label: "Spotlight" },
  { id: "B", label: "Resource browser" },
  { id: "C", label: "Event timeline" },
];

const BOOKABLE_EVENTS: AuditEvent[] = [
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

const USER_EVENTS: AuditEvent[] = [
  {
    id: "evt-8120",
    timestamp: "25 Aug 2026, 08:17:44",
    actor: "Priya Raman",
    username: "priya.raman",
    action: "WRITE",
    description: "Updated user profile US142",
    values: [
      ["Account enabled", "true"],
      ["Role", "User"],
    ],
  },
  {
    id: "evt-7021",
    timestamp: "03 Aug 2026, 11:03:19",
    actor: "System Administrator",
    username: "sysadmin1",
    action: "CREATE",
    description: "Created user account US142",
    values: [
      ["Account enabled", "true"],
      ["Role", "User"],
    ],
  },
];

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
        renderCell: ({ row }) => <RecordedValues values={row.values} />,
      },
    },
  ],
});

const RESOURCES = {
  bookableItem: [
    { id: "7", name: "Confocal microscope (Zeiss LSM 980)", detail: "IN123 · Europe/London" },
    { id: "11", name: "Cell culture room", detail: "IN244 · Europe/London" },
    { id: "18", name: "NMR spectrometer", detail: "IN319 · Europe/Berlin" },
  ],
  users: [
    { id: "142", name: "Priya Raman", detail: "priya.raman · You" },
    { id: "91", name: "Tomas Lindqvist", detail: "tomas.lindqvist" },
    { id: "4", name: "Morgan Ellis", detail: "morgan.ellis · System administrator" },
  ],
};

function initialVariant(): Variant {
  const value = new URLSearchParams(window.location.search).get("variant");
  return VARIANTS.find(({ id }) => id === value)?.id ?? "A";
}

function isTypingTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && (target.matches("input, textarea, select") || target.isContentEditable);
}

function VariantSwitcher({ variant, onChange }: { variant: Variant; onChange: (variant: Variant) => void }) {
  const currentIndex = VARIANTS.findIndex(({ id }) => id === variant);
  const move = (offset: number) => {
    const nextIndex = (currentIndex + offset + VARIANTS.length) % VARIANTS.length;
    const next = VARIANTS[nextIndex];
    onChange(next.id);
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) return;
      if (event.key === "ArrowLeft") move(-1);
      if (event.key === "ArrowRight") move(1);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  return (
    <nav
      aria-label="Prototype variants"
      className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-2 rounded-full border bg-background/95 p-1.5 shadow-xl backdrop-blur"
    >
      <Button type="button" variant="ghost" size="icon-sm" aria-label="Previous variant" onClick={() => move(-1)}>
        <ArrowLeftIcon aria-hidden="true" />
      </Button>
      <span className="min-w-36 text-center text-xs font-medium">
        {variant} · {VARIANTS[currentIndex].label}
      </span>
      <Button type="button" variant="ghost" size="icon-sm" aria-label="Next variant" onClick={() => move(1)}>
        <ArrowRightIcon aria-hidden="true" />
      </Button>
    </nav>
  );
}

function AuditPrototype(props: AuditPrototypeProps) {
  const [variant, setVariant] = useState<Variant>(initialVariant);
  const setVariantAndUrl = (next: Variant) => {
    const url = new URL(window.location.href);
    url.searchParams.set("variant", next);
    window.history.replaceState({}, "", url);
    setVariant(next);
  };

  return (
    <>
      {variant === "B" || variant === "C" ? null : <VariantA {...props} filterDisplay={variant} />}
      {variant === "B" ? <VariantB {...props} /> : null}
      {variant === "C" ? <VariantC {...props} /> : null}
      <VariantSwitcher variant={variant} onChange={setVariantAndUrl} />
    </>
  );
}

function useAuditSelection({ initialScope, role }: Pick<AuditPrototypeProps, "initialScope" | "role">) {
  const [scope, setScope] = useState(initialScope);
  const resources = scope === "bookable-item" ? RESOURCES.bookableItem : RESOURCES.users;
  const visibleResources = scope === "user" && role === "user" ? resources.slice(0, 1) : resources;
  const [selectedIds, setSelectedIds] = useState<Record<Scope, string>>({ "bookable-item": "7", user: "142" });
  const selectedId = selectedIds[scope];
  const selected = visibleResources.find(({ id }) => id === selectedId) ?? visibleResources[0];

  return {
    scope,
    setScope,
    resources: visibleResources,
    selected,
    selectResource: (id: string) => setSelectedIds({ ...selectedIds, [scope]: id }),
  };
}

const DEFAULT_RANGE = { from: "2026-08-01", to: "2026-08-25" };

const DATE_PRESETS = [
  { id: "7d", label: "Last 7 days", from: "2026-08-18" },
  { id: "30d", label: "Last 30 days", from: "2026-07-26" },
  { id: "90d", label: "Last 90 days", from: "2026-05-27" },
];

function formatDay(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

function useDateRange() {
  const [range, setRange] = useState(DEFAULT_RANGE);
  const preset = DATE_PRESETS.find(({ from }) => from === range.from && range.to === DEFAULT_RANGE.to);

  return {
    ...range,
    label: `${formatDay(range.from)} – ${formatDay(range.to)}`,
    presetId: preset?.id ?? "custom",
    setFrom: (from: string) => setRange((current) => ({ ...current, from })),
    setTo: (to: string) => setRange((current) => ({ ...current, to })),
    applyPreset: (from: string) => setRange({ from, to: DEFAULT_RANGE.to }),
    reset: () => setRange(DEFAULT_RANGE),
  };
}

function PageHeading({ role }: { role: Role }) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="space-y-2">
        <Heading level={2} as="h1">
          Audit log
        </Heading>
        <p className="max-w-2xl text-sm text-muted-foreground">
          Review recorded actions for one Booking resource. Results are ordered newest first.
        </p>
      </div>
      <Badge variant="outline" className="gap-1.5">
        <ShieldCheckIcon aria-hidden="true" />
        {role === "administrator" ? "System administrator" : "Authenticated user"}
      </Badge>
    </header>
  );
}

function ScopeButtons({ scope, setScope }: { scope: Scope; setScope: (scope: Scope) => void }) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">Resource type</legend>
      <div className="inline-flex rounded-full bg-muted p-1">
        <Button
          type="button"
          size="sm"
          variant={scope === "bookable-item" ? "secondary" : "ghost"}
          aria-pressed={scope === "bookable-item"}
          onClick={() => setScope("bookable-item")}
        >
          Bookable item
        </Button>
        <Button
          type="button"
          size="sm"
          variant={scope === "user" ? "secondary" : "ghost"}
          aria-pressed={scope === "user"}
          onClick={() => setScope("user")}
        >
          User
        </Button>
      </div>
    </fieldset>
  );
}

function FilterFields({
  scope,
  role,
  resources,
  selectedId,
  onSelect,
}: {
  scope: Scope;
  role: Role;
  resources: typeof RESOURCES.bookableItem;
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Field>
        <FieldLabel htmlFor="audit-resource">{scope === "bookable-item" ? "Bookable item" : "User"}</FieldLabel>
        <select
          id="audit-resource"
          className="h-9 w-full rounded-sm bg-input/50 px-3 text-sm"
          value={selectedId}
          onChange={(event) => onSelect(event.currentTarget.value)}
        >
          {resources.map((resource) => (
            <option key={resource.id} value={resource.id}>
              {resource.name}
            </option>
          ))}
        </select>
        {scope === "user" && role === "user" ? (
          <p className="text-xs text-muted-foreground">Your account is the only user available to you.</p>
        ) : null}
      </Field>
      <Field>
        <FieldLabel htmlFor="audit-from">From</FieldLabel>
        <Input id="audit-from" type="date" defaultValue="2026-08-01" />
      </Field>
      <Field>
        <FieldLabel htmlFor="audit-to">To</FieldLabel>
        <Input id="audit-to" type="date" defaultValue="2026-08-25" />
      </Field>
    </div>
  );
}

function SnapshotControls({ count }: { count: number }) {
  return (
    <div className="flex flex-wrap items-center justify-end gap-3">
      <div className="text-right">
        <p className="text-sm font-medium">
          {count} {count === 1 ? "event" : "events"} in snapshot
        </p>
        <p className="text-xs text-muted-foreground">Results as of 25 Aug 2026, 10:45:00 UTC</p>
      </div>
      <Button type="button" variant="outline" size="sm">
        <RefreshCwIcon aria-hidden="true" />
        Refresh
      </Button>
    </div>
  );
}

function RecordedValues({ values }: { values: AuditEvent["values"] }) {
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

function StateContent({ state, children }: { state: ViewState; children: React.ReactNode }) {
  if (state === "conflict") {
    return (
      <Alert>
        <AlertTriangleIcon aria-hidden="true" />
        <AlertTitle>The audit results changed</AlertTitle>
        <AlertDescription>
          This snapshot can no longer be paged reliably. Restart from the first page to prevent missing or duplicate
          events.
          <div className="mt-4">
            <Button type="button" size="sm">
              Restart from first page
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    );
  }

  if (state === "unavailable") {
    return (
      <Alert variant="destructive">
        <AlertTriangleIcon aria-hidden="true" />
        <AlertTitle>Audit log unavailable</AlertTitle>
        <AlertDescription>
          The complete result set could not be read. No partial results are shown.
          <div className="mt-4">
            <Button type="button" variant="outline" size="sm">
              Try again
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    );
  }

  return children;
}

function AuditEventList({
  events,
  state,
  renderRows,
}: {
  events: readonly AuditEvent[];
  state: ViewState;
  renderRows?: (events: readonly AuditEvent[]) => React.ReactNode;
}) {
  const count = state === "populated" ? events.length : 0;
  const table = useTableList({
    config: auditEventConfig,
    dataSource: { type: "client", rows: state === "populated" ? events : [] },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });
  if (state === "conflict" || state === "unavailable") {
    return (
      <div className="space-y-4">
        <SnapshotControls count={count} />
        <StateContent state={state}>{null}</StateContent>
      </div>
    );
  }

  return (
    <TableList
      {...table.tableProps}
      status={state === "loading" ? "loading" : "idle"}
      presentations={{ table: "wide", cards: "narrow" }}
      createAction={<SnapshotControls count={count} />}
      renderRows={renderRows}
      emptyDescription="Try a different date range or resource."
      variant="transparent"
    />
  );
}

/* ------------------------------------------ variant A: filter display shell -- */

// Muted text on a tinted panel misses the contrast gate, so every surface below
// keeps `text-muted-foreground` on plain `bg-background`/`bg-card`.

type AuditSelection = ReturnType<typeof useAuditSelection>;
type AuditDateRange = ReturnType<typeof useDateRange>;
type FilterDisplayProps = { selection: AuditSelection; range: AuditDateRange; role: Role };

const SCOPES: Array<{ id: Scope; label: string; icon: typeof UserRoundIcon }> = [
  { id: "bookable-item", label: "Bookable item", icon: CalendarClockIcon },
  { id: "user", label: "User", icon: UserRoundIcon },
];

function scopeLabel(scope: Scope): string {
  return scope === "bookable-item" ? "Bookable item" : "User";
}

function scopeIcon(scope: Scope): typeof UserRoundIcon {
  return scope === "bookable-item" ? CalendarClockIcon : UserRoundIcon;
}

/** Pill segmented control. Replaces ScopeButtons for the A family only. */
function ScopeToggle({
  scope,
  setScope,
  className,
}: {
  scope: Scope;
  setScope: (scope: Scope) => void;
  className?: string;
}) {
  return (
    <fieldset className={cn("inline-flex shrink-0 items-center gap-1 rounded-full border bg-muted p-1", className)}>
      <legend className="sr-only">Resource type</legend>
      {SCOPES.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          aria-pressed={scope === id}
          onClick={() => setScope(id)}
          className={cn(
            "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium transition-colors",
            scope === id ? "bg-background text-foreground shadow-sm" : "text-foreground hover:bg-background/60",
          )}
        >
          <Icon aria-hidden="true" className="size-4" />
          {label}
        </button>
      ))}
    </fieldset>
  );
}

function ResourceSelect({ id, label, selection }: { id: string; label?: string; selection: AuditSelection }) {
  const Icon = scopeIcon(selection.scope);

  return (
    <InputGroup>
      <InputGroupAddon>
        <Icon aria-hidden="true" />
      </InputGroupAddon>
      <select
        id={id}
        aria-label={label}
        className="h-9 min-w-0 flex-1 cursor-pointer bg-transparent pr-3 text-sm outline-none"
        value={selection.selected.id}
        onChange={(event) => selection.selectResource(event.currentTarget.value)}
      >
        {selection.resources.map((resource) => (
          <option key={resource.id} value={resource.id}>
            {resource.name}
          </option>
        ))}
      </select>
    </InputGroup>
  );
}

function DateRangeGroup({ range, className }: { range: AuditDateRange; className?: string }) {
  return (
    <InputGroup className={cn("w-auto", className)}>
      <InputGroupAddon>
        <CalendarRangeIcon aria-hidden="true" />
      </InputGroupAddon>
      <input
        aria-label="From date"
        className="h-9 bg-transparent text-sm outline-none"
        type="date"
        value={range.from}
        onChange={(event) => range.setFrom(event.currentTarget.value)}
      />
      <InputGroupText aria-hidden="true" className="px-2">
        –
      </InputGroupText>
      <input
        aria-label="To date"
        className="h-9 bg-transparent pr-3 text-sm outline-none"
        type="date"
        value={range.to}
        onChange={(event) => range.setTo(event.currentTarget.value)}
      />
    </InputGroup>
  );
}

function OnlyYourAccountNote({ selection, role }: Pick<FilterDisplayProps, "selection" | "role">) {
  if (selection.scope !== "user" || role !== "user") return null;
  return <FieldDescription>Your account is the only user available to you.</FieldDescription>;
}

function ScopeFieldGrid({ selection, range, role, idPrefix }: FilterDisplayProps & { idPrefix: string }) {
  return (
    <div className="grid gap-4 sm:grid-cols-[minmax(0,2fr)_repeat(2,minmax(0,1fr))]">
      <Field>
        <FieldLabel htmlFor={`${idPrefix}-resource`}>{scopeLabel(selection.scope)}</FieldLabel>
        <ResourceSelect id={`${idPrefix}-resource`} selection={selection} />
        <OnlyYourAccountNote selection={selection} role={role} />
      </Field>
      <Field>
        <FieldLabel htmlFor={`${idPrefix}-from`}>From</FieldLabel>
        <Input
          id={`${idPrefix}-from`}
          type="date"
          value={range.from}
          onChange={(event) => range.setFrom(event.currentTarget.value)}
        />
      </Field>
      <Field>
        <FieldLabel htmlFor={`${idPrefix}-to`}>To</FieldLabel>
        <Input
          id={`${idPrefix}-to`}
          type="date"
          value={range.to}
          onChange={(event) => range.setTo(event.currentTarget.value)}
        />
      </Field>
    </div>
  );
}

/** A — the filter fields sit in a titled card, with the scope toggle as the card action. */
function ScopeCardFilters(props: FilterDisplayProps) {
  return (
    <Card>
      <CardHeader className="border-b">
        <CardTitle className="flex items-center gap-2">
          <SlidersHorizontalIcon aria-hidden="true" className="size-4 text-muted-foreground" />
          Audit scope
        </CardTitle>
        <CardDescription>Pick one resource and a date range, then load a snapshot.</CardDescription>
        <CardAction>
          <ScopeToggle scope={props.selection.scope} setScope={props.selection.setScope} />
        </CardAction>
      </CardHeader>
      <CardContent>
        <ScopeFieldGrid {...props} idPrefix="audit-a" />
      </CardContent>
      <CardFooter className="justify-between gap-3 border-t">
        <p className="text-sm text-muted-foreground">{props.range.label}</p>
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={props.range.reset}>
            Reset dates
          </Button>
          <Button type="button">
            <SearchIcon aria-hidden="true" />
            Load snapshot
          </Button>
        </div>
      </CardFooter>
    </Card>
  );
}

/** A2 — one dense sticky row, so the filter stays reachable while scrolling the table. */
function ToolbarFilters({ selection, range, role }: FilterDisplayProps) {
  return (
    <div className="space-y-2">
      <div className="sticky top-0 z-20 flex flex-wrap items-center gap-2 rounded-4xl border bg-background p-2 shadow-md">
        <ScopeToggle scope={selection.scope} setScope={selection.setScope} />
        <div className="min-w-56 flex-1">
          <ResourceSelect id="audit-a2-resource" label={scopeLabel(selection.scope)} selection={selection} />
        </div>
        <DateRangeGroup range={range} className="shrink-0" />
        <Button type="button" className="shrink-0">
          <SearchIcon aria-hidden="true" />
          Load snapshot
        </Button>
      </div>
      <p className="px-4 text-xs text-muted-foreground">
        {scopeLabel(selection.scope)} · {selection.selected.name} · {range.label}
        {selection.scope === "user" && role === "user" ? " · Your account is the only user available to you." : ""}
      </p>
    </div>
  );
}

/** A3 — the filter reads as a sentence of chips; the controls stay folded away until asked for. */
function QueryChipFilters(props: FilterDisplayProps) {
  const { selection, range } = props;
  const [open, setOpen] = useState(false);
  const ScopeIcon = scopeIcon(selection.scope);

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <div className="rounded-4xl border bg-background shadow-md">
        <div className="flex flex-wrap items-center gap-2 p-3 pl-5">
          <span className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Showing</span>
          <Badge variant="secondary" className="h-7 gap-1.5 px-2.5">
            <ScopeIcon aria-hidden="true" />
            {scopeLabel(selection.scope)}
          </Badge>
          <Badge variant="secondary" className="h-7 max-w-72 gap-1.5 px-2.5">
            <span className="truncate">{selection.selected.name}</span>
          </Badge>
          <Badge variant="secondary" className="h-7 gap-1.5 px-2.5">
            <CalendarRangeIcon aria-hidden="true" />
            {range.label}
          </Badge>
          <CollapsibleTrigger className={cn(buttonVariants({ variant: "outline", size: "sm" }), "ml-auto gap-1.5")}>
            <SlidersHorizontalIcon aria-hidden="true" />
            {open ? "Hide filters" : "Edit filters"}
            <ChevronDownIcon aria-hidden="true" className={cn("transition-transform", open && "rotate-180")} />
          </CollapsibleTrigger>
        </div>
        <CollapsibleContent className="border-t">
          <div className="space-y-5 p-5">
            <ScopeToggle scope={selection.scope} setScope={selection.setScope} />
            <ScopeFieldGrid {...props} idPrefix="audit-a3" />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={range.reset}>
                Reset dates
              </Button>
              <Button type="button" onClick={() => setOpen(false)}>
                Apply
              </Button>
            </div>
          </div>
        </CollapsibleContent>
      </div>
    </Collapsible>
  );
}

/** A4 — resource first: the thing being audited is the headline, the period is a row of presets. */
function SpotlightFilters({ selection, range, role }: FilterDisplayProps) {
  const ScopeIcon = scopeIcon(selection.scope);

  return (
    <section aria-label="Audit scope" className="overflow-hidden rounded-4xl border bg-background shadow-md">
      <div className="flex flex-wrap items-center gap-4 p-5">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-3xl bg-primary text-primary-foreground">
          <ScopeIcon aria-hidden="true" className="size-6" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {scopeLabel(selection.scope)}
          </p>
          <Heading level={4} as="h2" className="mt-0.5 truncate">
            {selection.selected.name}
          </Heading>
          <p className="mt-1 truncate text-sm text-muted-foreground">
            {selection.selected.detail}
            {selection.scope === "user" && role === "user" ? " · The only user available to you" : ""}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <ScopeToggle scope={selection.scope} setScope={selection.setScope} />
          <Menu>
            <MenuTrigger className={cn(buttonVariants({ variant: "outline" }), "gap-1.5")}>
              Change
              <ChevronDownIcon aria-hidden="true" />
            </MenuTrigger>
            <MenuContent>
              {selection.resources.map((resource) => (
                <MenuItem
                  key={resource.id}
                  className="items-start gap-3"
                  onClick={() => selection.selectResource(resource.id)}
                >
                  <CheckIcon
                    aria-hidden="true"
                    className={cn("mt-0.5 size-4 shrink-0", resource.id !== selection.selected.id && "invisible")}
                  />
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{resource.name}</span>
                    <span className="block truncate text-xs text-muted-foreground">{resource.detail}</span>
                  </span>
                </MenuItem>
              ))}
            </MenuContent>
          </Menu>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t p-4 pl-5">
        <span className="mr-1 text-sm font-medium">Period</span>
        {DATE_PRESETS.map((preset) => (
          <Button
            key={preset.id}
            type="button"
            size="sm"
            variant={range.presetId === preset.id ? "default" : "outline"}
            aria-pressed={range.presetId === preset.id}
            onClick={() => range.applyPreset(preset.from)}
          >
            {preset.label}
          </Button>
        ))}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <DateRangeGroup range={range} />
          <Button type="button">
            <SearchIcon aria-hidden="true" />
            Load snapshot
          </Button>
        </div>
      </div>
    </section>
  );
}

const FILTER_DISPLAYS: Record<FilterDisplay, (props: FilterDisplayProps) => React.ReactElement> = {
  A: ScopeCardFilters,
  A2: ToolbarFilters,
  A3: QueryChipFilters,
  A4: SpotlightFilters,
};

function VariantA({ filterDisplay, ...props }: AuditPrototypeProps & { filterDisplay: FilterDisplay }) {
  const selection = useAuditSelection(props);
  const range = useDateRange();
  const events = selection.scope === "bookable-item" ? BOOKABLE_EVENTS : USER_EVENTS;
  const Filters = FILTER_DISPLAYS[filterDisplay];

  return (
    <main className="mx-auto max-w-7xl space-y-7 p-4 pb-24 sm:p-8 sm:pb-24">
      <PageHeading role={props.role} />
      <Filters selection={selection} range={range} role={props.role} />
      <AuditEventList events={events} state={props.viewState} />
    </main>
  );
}

function ResourceList({
  scope,
  resources,
  selectedId,
  onSelect,
}: {
  scope: Scope;
  resources: typeof RESOURCES.bookableItem;
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="space-y-2">
      {resources.map((resource) => (
        <button
          className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors ${
            resource.id === selectedId ? "border-primary bg-primary/5" : "hover:bg-muted"
          }`}
          key={resource.id}
          type="button"
          onClick={() => onSelect(resource.id)}
        >
          {scope === "user" ? <UserRoundIcon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />}
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{resource.name}</span>
            <span className="block truncate text-xs text-muted-foreground">{resource.detail}</span>
          </span>
          <ChevronRightIcon aria-hidden="true" className="size-4 text-muted-foreground" />
        </button>
      ))}
    </div>
  );
}

function LedgerRow({ event }: { event: AuditEvent }) {
  return (
    <article className="grid gap-4 border-b py-5 last:border-0 lg:grid-cols-[11rem_1fr_auto]">
      <div>
        <p className="text-sm font-medium">{event.timestamp}</p>
        <p className="mt-1 text-xs text-muted-foreground">Event {event.id}</p>
      </div>
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">{event.action}</Badge>
          <p className="font-medium">{event.description}</p>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">
          {event.actor} · {event.username}
        </p>
      </div>
      <RecordedValues values={event.values} />
    </article>
  );
}

function VariantB(props: AuditPrototypeProps) {
  const selection = useAuditSelection(props);
  const events = selection.scope === "bookable-item" ? BOOKABLE_EVENTS : USER_EVENTS;

  return (
    <main className="mx-auto max-w-7xl p-4 pb-24 sm:p-8 sm:pb-24">
      <PageHeading role={props.role} />
      <Separator className="my-7" />
      <div className="grid overflow-hidden rounded-xl border lg:grid-cols-[21rem_1fr]">
        <aside className="space-y-5 border-b bg-muted/20 p-5 lg:border-r lg:border-b-0">
          <ScopeButtons scope={selection.scope} setScope={selection.setScope} />
          {selection.scope === "user" && props.role === "user" ? (
            <p className="text-xs text-muted-foreground">Only your account is returned by the user service.</p>
          ) : null}
          <ResourceList
            scope={selection.scope}
            resources={selection.resources}
            selectedId={selection.selected.id}
            onSelect={selection.selectResource}
          />
        </aside>
        <section className="min-w-0 p-5 sm:p-7" aria-label={`Audit events for ${selection.selected.name}`}>
          <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Selected resource</p>
              <Heading level={4} as="h2" className="mt-1">
                {selection.selected.name}
              </Heading>
              <p className="mt-1 text-sm text-muted-foreground">{selection.selected.detail}</p>
            </div>
            <div className="flex gap-2">
              <Input aria-label="From date" type="date" defaultValue="2026-08-01" className="w-auto" />
              <Input aria-label="To date" type="date" defaultValue="2026-08-25" className="w-auto" />
            </div>
          </div>
          <div className="mt-4">
            <AuditEventList
              events={events}
              state={props.viewState}
              renderRows={(visibleEvents) => (
                <div>
                  {visibleEvents.map((event) => (
                    <LedgerRow event={event} key={event.id} />
                  ))}
                </div>
              )}
            />
          </div>
        </section>
      </div>
    </main>
  );
}

function TimelineEvent({ event }: { event: AuditEvent }) {
  return (
    <li className="relative grid gap-3 pb-8 pl-10 last:pb-0 before:absolute before:top-7 before:bottom-0 before:left-3 before:w-px before:bg-border last:before:hidden">
      <span className="absolute top-1 left-0 flex size-7 items-center justify-center rounded-full border bg-background">
        <FileClockIcon aria-hidden="true" className="size-4" />
      </span>
      <div className="flex flex-wrap items-center gap-2">
        <time className="text-sm font-medium">{event.timestamp}</time>
        <Badge variant="outline">{event.action}</Badge>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{event.description}</CardTitle>
          <p className="text-sm text-muted-foreground">
            {event.actor} · {event.username}
          </p>
        </CardHeader>
        <CardContent>
          <p className="mb-3 text-xs font-medium tracking-wide text-muted-foreground uppercase">Recorded values</p>
          <RecordedValues values={event.values} />
        </CardContent>
      </Card>
    </li>
  );
}

function VariantC(props: AuditPrototypeProps) {
  const selection = useAuditSelection(props);
  const events = selection.scope === "bookable-item" ? BOOKABLE_EVENTS : USER_EVENTS;

  return (
    <main className="mx-auto max-w-5xl space-y-7 p-4 pb-24 sm:p-8 sm:pb-24">
      <PageHeading role={props.role} />
      <Card className="bg-muted/20">
        <CardContent className="grid gap-5 pt-6 md:grid-cols-[auto_1fr_auto] md:items-end">
          <ScopeButtons scope={selection.scope} setScope={selection.setScope} />
          <FilterFields
            scope={selection.scope}
            role={props.role}
            resources={selection.resources}
            selectedId={selection.selected.id}
            onSelect={selection.selectResource}
          />
          <Button type="button" variant="outline">
            Load snapshot
          </Button>
        </CardContent>
      </Card>
      <div className="rounded-xl bg-primary/5 p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm text-muted-foreground">Audit history for</p>
            <Heading level={3} as="h2" className="mt-1">
              {selection.selected.name}
            </Heading>
            <p className="mt-1 text-sm text-muted-foreground">{selection.selected.detail}</p>
          </div>
        </div>
      </div>
      <AuditEventList
        events={events}
        state={props.viewState}
        renderRows={(visibleEvents) => (
          <ol>
            {visibleEvents.map((event) => (
              <TimelineEvent event={event} key={event.id} />
            ))}
          </ol>
        )}
      />
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Audit log views",
  component: AuditPrototype,
  parameters: { layout: "fullscreen" },
  args: {
    initialScope: "bookable-item",
    role: "user",
    viewState: "populated",
  },
  argTypes: {
    initialScope: { control: "inline-radio", options: ["bookable-item", "user"] },
    role: { control: "inline-radio", options: ["user", "administrator"] },
    viewState: { control: "select", options: ["populated", "empty", "loading", "conflict", "unavailable"] },
  },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof AuditPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const BookableItemPopulated: Story = {};
export const UserAuditAsAdministrator: Story = {
  args: { initialScope: "user", role: "administrator" },
};
export const UserAuditAsOrdinaryUser: Story = {
  args: { initialScope: "user", role: "user" },
};
export const Empty: Story = { args: { viewState: "empty" } };
export const Loading: Story = { args: { viewState: "loading" } };
export const SnapshotConflict: Story = { args: { viewState: "conflict" } };
export const AuditUnavailable: Story = { args: { viewState: "unavailable" } };
