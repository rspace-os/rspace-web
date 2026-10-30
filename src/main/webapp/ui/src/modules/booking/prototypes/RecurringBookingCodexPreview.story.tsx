// PROTOTYPE ONLY. Three Storybook concepts for the proposed recurring-booking flow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy is intentionally not entering the translation catalog. */

import { AlertTriangleIcon, CheckIcon, InfoIcon, Repeat2Icon } from "lucide-react";
import { HttpResponse, http } from "msw";
import { setupWorker } from "msw/browser";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import type { BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { Booking } from "@/modules/booking/domain/booking";
import { formatBookingAgendaTimeRange } from "@/modules/booking/domain/bookingAgenda";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import {
  type CodexOccurrence,
  type CodexRule,
  type CodexScenario,
  type CodexWeekday,
  codexScenarios,
} from "./recurringBookingCodexFixtures";

type PreviewPhase = "ready" | "checking" | "error";
export const emptyRows: readonly CodexOccurrence[] = [];
export const prototypeDisplayTimezone = "Europe/London";

function bookingTarget(item: BookableItemOption): NonNullable<Booking["target"]> {
  return {
    relationTo: "instruments",
    value: { id: item.targetId, name: item.name, deleted: false },
    globalId: item.globalId,
  };
}

function scheduleBookingFixture(
  id: number,
  item: BookableItemOption,
  start: string,
  end: string,
  kind: Booking["kind"] = "BOOKING",
): Booking {
  return {
    id,
    version: 1,
    target: bookingTarget(item),
    timezone: item.timezone,
    start,
    end,
    state: "CONFIRMED",
    kind,
    privacy: "full",
    cancellationReason: null,
    purpose: kind === "MAINTENANCE" ? "Scheduled maintenance" : "Cell imaging protocol",
    bookedBy: "Ada Lovelace (ada)",
    createdBy: "Ada Lovelace (ada)",
    canEdit: false,
    canCancel: false,
    createdAt: start,
    updatedAt: start,
  };
}

const dayScheduleFixtures = [
  scheduleBookingFixture(8901, codexScenarios.ready.target, "2026-10-05T13:00:00Z", "2026-10-05T14:00:00Z"),
  scheduleBookingFixture(8902, codexScenarios.dst.target, "2027-03-07T11:00:00Z", "2027-03-07T11:30:00Z"),
];

function dayScheduleForRequest(request: Request): Booking[] {
  const url = new URL(request.url);
  const where = url.searchParams.get("where") ?? "";
  const globalIds = where.match(/target=in=\(([^)]+)\)/)?.[1]?.split(",") ?? [];
  const before = where.match(/start=lt=([^;]+)/)?.[1];
  const after = where.match(/end=gt=([^;]+)/)?.[1];
  return dayScheduleFixtures.filter(
    (booking) =>
      globalIds.includes(booking.target.globalId) &&
      (!before || Date.parse(booking.start) < Date.parse(before)) &&
      (!after || Date.parse(booking.end) > Date.parse(after)),
  );
}

export const prototypeWorker = setupWorker(
  http.get("/api/v2/bookings", ({ request }) => {
    const docs = dayScheduleForRequest(request);
    return HttpResponse.json({ docs, totalDocs: docs.length, totalPages: 1, page: 1, hasNextPage: false });
  }),
);

const weekdays: ReadonlyArray<{ value: CodexWeekday; label: string; short: string }> = [
  { value: 1, label: "Monday", short: "Mon" },
  { value: 2, label: "Tuesday", short: "Tue" },
  { value: 3, label: "Wednesday", short: "Wed" },
  { value: 4, label: "Thursday", short: "Thu" },
  { value: 5, label: "Friday", short: "Fri" },
  { value: 6, label: "Saturday", short: "Sat" },
  { value: 7, label: "Sunday", short: "Sun" },
];

export function formatDate(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

function addOneYear(date: string): string {
  if (!date) return "";
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCFullYear(value.getUTCFullYear() + 1);
  return value.toISOString().slice(0, 10);
}

function weekdayFromDate(date: string): CodexWeekday {
  if (!date) return 1;
  const day = new Date(`${date}T12:00:00Z`).getUTCDay();
  return (day === 0 ? 7 : day) as CodexWeekday;
}

function sameWindow(left: BookingFormState["draft"] | undefined, right: BookingFormState["draft"]): boolean {
  return Boolean(
    left &&
      left.startDate === right.startDate &&
      left.startTime === right.startTime &&
      left.endDate === right.endDate &&
      left.endTime === right.endTime &&
      left.startOccurrence === right.startOccurrence &&
      left.endOccurrence === right.endOccurrence,
  );
}

function sameWeekdays(left: readonly CodexWeekday[], right: readonly CodexWeekday[]): boolean {
  return left.length === right.length && [...left].sort().every((day, index) => day === [...right].sort()[index]);
}

function previewRows(
  scenario: (typeof codexScenarios)[CodexScenario],
  rule: CodexRule,
  draft: BookingFormState["draft"] | undefined,
): { kind: "ready"; rows: readonly CodexOccurrence[] } | { kind: "unsupported"; reason: string } {
  if (!sameWindow(draft, scenario.window)) {
    return { kind: "unsupported", reason: "The booking window changed; this example has no matching server response." };
  }
  if (rule.frequency === "NONE") return { kind: "ready", rows: scenario.occurrences.slice(0, 1) };
  const matchesRule =
    rule.frequency === scenario.rule.frequency &&
    rule.interval === scenario.rule.interval &&
    sameWeekdays(rule.weekdays, scenario.rule.weekdays);
  if (!matchesRule || (rule.ends.kind === "count" && scenario.rule.ends.kind !== "count")) {
    return { kind: "unsupported", reason: "The repeat rule changed; this example has no matching server response." };
  }
  if (scenario.occurrences.length === 0) {
    return { kind: "unsupported", reason: "This fixture has no returned occurrence rows to display." };
  }
  if (rule.ends.kind === "count") {
    if (rule.ends.count > scenario.occurrences.length) {
      return {
        kind: "unsupported",
        reason: `This fixture contains ${scenario.occurrences.length} returned rows, so it cannot preview ${rule.ends.count}.`,
      };
    }
    return { kind: "ready", rows: scenario.occurrences.slice(0, rule.ends.count) };
  }
  const lastFixtureDate = scenario.occurrences[scenario.occurrences.length - 1]?.date ?? scenario.window.startDate;
  if (
    rule.ends.until < scenario.window.startDate ||
    rule.ends.until > addOneYear(scenario.window.startDate) ||
    rule.ends.until > lastFixtureDate
  ) {
    return { kind: "unsupported", reason: "Choose an end date from the first booking through one year later." };
  }
  const until = rule.ends.until;
  const rows = scenario.occurrences.filter((row) => row.date <= until);
  if (rows.length < 2) {
    return { kind: "unsupported", reason: "This fixture needs at least two returned rows for an end-date preview." };
  }
  return { kind: "ready", rows };
}

export function RepeatControls({
  rule,
  anchorDate,
  onChange,
}: {
  rule: CodexRule;
  anchorDate: string;
  onChange: (next: CodexRule) => void;
}) {
  const id = `repeat-${rule.frequency}-${anchorDate}`;
  const anchorWeekday = weekdayFromDate(anchorDate);
  const selectedDays = new Set([...rule.weekdays, anchorWeekday]);
  const weekly = rule.frequency === "WEEKLY";
  const repeating = rule.frequency !== "NONE";
  return (
    <FieldSet className="gap-3 rounded-sm border bg-muted/20 p-4">
      <FieldLegend variant="label" className="flex items-center gap-2">
        <Repeat2Icon aria-hidden="true" className="size-4" /> Repeat
        <Badge variant="outline" className="ml-1 font-normal">
          Example default · provisional
        </Badge>
      </FieldLegend>
      <RadioGroup
        aria-label="Repeat frequency"
        value={rule.frequency}
        onValueChange={(value) => onChange({ ...rule, frequency: value as CodexRule["frequency"] })}
        className="flex flex-wrap gap-x-5 gap-y-2"
      >
        {(["NONE", "DAILY", "WEEKLY"] as const).map((frequency) => {
          const label = frequency === "NONE" ? "Does not repeat" : frequency === "DAILY" ? "Daily" : "Weekly";
          return (
            <div className="flex items-center gap-2" key={frequency}>
              <RadioGroupItem id={`${id}-${frequency}`} value={frequency} />
              <Label htmlFor={`${id}-${frequency}`}>{label}</Label>
            </div>
          );
        })}
      </RadioGroup>
      {repeating ? (
        <>
          <div className="flex items-center gap-2 text-sm">
            <Label htmlFor={`${id}-interval`}>Every</Label>
            <Input
              id={`${id}-interval`}
              type="number"
              min={1}
              max={4}
              className="w-16"
              value={rule.interval}
              onChange={(event) =>
                onChange({ ...rule, interval: Math.min(4, Math.max(1, Number(event.currentTarget.value) || 1)) })
              }
            />
            <span>{weekly ? (rule.interval === 1 ? "week" : "weeks") : rule.interval === 1 ? "day" : "days"}</span>
          </div>
          {weekly ? (
            <FieldSet className="gap-2">
              <FieldLegend variant="label">On</FieldLegend>
              <div className="flex flex-wrap gap-x-4 gap-y-2">
                {weekdays.map((day) => {
                  const anchor = day.value === anchorWeekday;
                  return (
                    <Label className="flex items-center gap-2" key={day.value}>
                      <Checkbox
                        checked={selectedDays.has(day.value)}
                        disabled={anchor}
                        aria-label={anchor ? `${day.label}, includes the first booking` : day.label}
                        onCheckedChange={(checked) =>
                          onChange({
                            ...rule,
                            weekdays:
                              checked === true
                                ? ([...new Set([...rule.weekdays, day.value, anchorWeekday])].sort() as CodexWeekday[])
                                : ([
                                    ...new Set([
                                      ...rule.weekdays.filter(
                                        (value) => value !== day.value && value !== anchorWeekday,
                                      ),
                                      anchorWeekday,
                                    ]),
                                  ].sort() as CodexWeekday[]),
                          })
                        }
                      />
                      <span>
                        {day.short}
                        {anchor ? " · first" : ""}
                      </span>
                    </Label>
                  );
                })}
              </div>
            </FieldSet>
          ) : null}
          <FieldSet className="gap-2">
            <FieldLegend variant="label">Ends</FieldLegend>
            <RadioGroup
              aria-label="Repeat ending"
              value={rule.ends.kind}
              onValueChange={(value) =>
                onChange({
                  ...rule,
                  ends:
                    value === "until" ? { kind: "until", until: addOneYear(anchorDate) } : { kind: "count", count: 12 },
                })
              }
              className="gap-2"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem id={`${id}-count`} value="count" />
                <Label htmlFor={`${id}-count`}>After</Label>
                <Input
                  aria-label="Number of occurrences"
                  type="number"
                  min={2}
                  max={52}
                  className="w-20"
                  value={rule.ends.kind === "count" ? rule.ends.count : 12}
                  disabled={rule.ends.kind !== "count"}
                  onChange={(event) =>
                    onChange({
                      ...rule,
                      ends: { kind: "count", count: Math.min(52, Math.max(2, Number(event.currentTarget.value) || 2)) },
                    })
                  }
                />
                <span className="text-sm">occurrences</span>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem id={`${id}-until`} value="until" />
                <Label htmlFor={`${id}-until`}>On</Label>
                <Input
                  aria-label="Last occurrence date"
                  type="date"
                  className="w-44"
                  min={anchorDate}
                  max={addOneYear(anchorDate)}
                  value={rule.ends.kind === "until" ? rule.ends.until : ""}
                  disabled={rule.ends.kind !== "until"}
                  onChange={(event) => onChange({ ...rule, ends: { kind: "until", until: event.currentTarget.value } })}
                />
              </div>
            </RadioGroup>
          </FieldSet>
          <p className="text-xs text-muted-foreground">
            Same wall-clock time in the display timezone. Scheduling rules are checked in the item timezone; the preview
            shows that time when you focus an occurrence. Limit: 52 occurrences within one year.
          </p>
        </>
      ) : null}
    </FieldSet>
  );
}

function occurrenceStatus(row: CodexOccurrence) {
  if (row.status === "overlap") {
    return (
      <>
        <Badge variant="destructive">Conflict</Badge>
        <span className="mt-1 block text-xs text-muted-foreground">{row.conflictWith}</span>
      </>
    );
  }
  if (row.status === "openingHours") return <Badge variant="destructive">Outside hours</Badge>;
  if (row.status === "dstShifted") {
    return (
      <>
        <Badge variant="outline" className="border-amber-600 text-amber-800">
          Shifted
        </Badge>
        <span className="mt-1 block max-w-52 text-xs text-muted-foreground">{row.note}</span>
      </>
    );
  }
  return <Badge variant="secondary">Available</Badge>;
}

function OccurrenceTable({
  rows,
  displayTimezone,
  instrumentTimezone,
}: {
  rows: readonly CodexOccurrence[];
  displayTimezone: string;
  instrumentTimezone: string;
}) {
  const { i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-10">#</TableHead>
          <TableHead>Date</TableHead>
          <TableHead>Time</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            key={row.id}
            className={row.status === "overlap" || row.status === "openingHours" ? "bg-destructive/5" : ""}
          >
            <TableCell className="text-muted-foreground">{row.position}</TableCell>
            <TableCell>
              {new Intl.DateTimeFormat(i18n.resolvedLanguage ?? i18n.language, {
                weekday: "short",
                day: "numeric",
                month: "short",
                year: "numeric",
                timeZone: displayTimezone,
              }).format(new Date(row.startInstant))}
            </TableCell>
            <TableCell className="whitespace-nowrap">
              <BookingInstrumentTimeTooltip
                start={row.startInstant}
                end={row.endInstant}
                displayTimeZone={displayTimezone}
                instrumentTimeZone={instrumentTimezone}
              >
                <time dateTime={row.startInstant}>
                  {formatBookingAgendaTimeRange(
                    row.startInstant,
                    row.endInstant,
                    displayTimezone,
                    i18n.resolvedLanguage ?? i18n.language,
                    timeFormat,
                  )}
                </time>
              </BookingInstrumentTimeTooltip>
            </TableCell>
            <TableCell>{occurrenceStatus(row)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function PreviewPanel({
  scenario,
  result,
  phase,
  displayTimezone = prototypeDisplayTimezone,
  onRetry,
  onLoadFixture,
}: {
  scenario: (typeof codexScenarios)[CodexScenario];
  result: ReturnType<typeof previewRows>;
  phase: PreviewPhase;
  displayTimezone?: string;
  onRetry: () => void;
  onLoadFixture: () => void;
}) {
  const rows = result.kind === "ready" ? result.rows : emptyRows;
  const blocking = rows.filter((row) => row.status === "overlap" || row.status === "openingHours").length;
  const shifted = rows.filter((row) => row.status === "dstShifted").length;
  return (
    <Card size="sm" className="gap-0 py-0">
      <CardHeader className="border-b py-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle className="text-base">Occurrence preview</CardTitle>
            <p className="mt-1 text-xs text-muted-foreground">
              Fixture dry run · times in {displayTimezone}; focus a time to see {scenario.target.timezone}
            </p>
          </div>
          {result.kind === "ready" ? <Badge variant="outline">{rows.length} returned rows</Badge> : null}
        </div>
      </CardHeader>
      {result.kind === "unsupported" ? (
        <CardContent className="p-4">
          <Alert>
            <InfoIcon />
            <AlertTitle>Preview invalidated</AlertTitle>
            <AlertDescription>{result.reason} Load this fixture to restore its exact example inputs.</AlertDescription>
          </Alert>
          <Button className="mt-3" variant="outline" onClick={onLoadFixture}>
            Load fixture
          </Button>
        </CardContent>
      ) : phase === "checking" ? (
        <CardContent className="p-4">
          <Alert>
            <InfoIcon />
            <AlertTitle>Checking every occurrence…</AlertTitle>
            <AlertDescription>Showing a simulated dry-run request.</AlertDescription>
          </Alert>
        </CardContent>
      ) : phase === "error" ? (
        <CardContent className="p-4">
          <Alert variant="destructive">
            <AlertTriangleIcon />
            <AlertTitle>Preview could not be loaded</AlertTitle>
            <AlertDescription>Nothing can be saved until every occurrence has a dry-run result.</AlertDescription>
          </Alert>
          <Button className="mt-3" variant="outline" onClick={onRetry}>
            Try preview again
          </Button>
        </CardContent>
      ) : (
        <>
          <CardContent className="border-b p-4">
            {blocking > 0 ? (
              <Alert variant="destructive">
                <AlertTriangleIcon />
                <AlertTitle>
                  {blocking} of {rows.length} occurrences cannot be booked
                </AlertTitle>
                <AlertDescription>The whole series is blocked until every conflict is resolved.</AlertDescription>
              </Alert>
            ) : shifted > 0 ? (
              <Alert>
                <InfoIcon />
                <AlertTitle>All {rows.length} occurrences are available</AlertTitle>
                <AlertDescription>
                  {shifted} time shifts forward for daylight saving time. The shifted row remains bookable.
                </AlertDescription>
              </Alert>
            ) : (
              <Alert>
                <CheckIcon />
                <AlertTitle>All {rows.length} occurrences are available</AlertTitle>
                <AlertDescription>The server checks the complete series again when it is saved.</AlertDescription>
              </Alert>
            )}
          </CardContent>
          <div className="[&_td]:whitespace-normal [&_th]:whitespace-normal">
            <OccurrenceTable
              rows={rows}
              displayTimezone={displayTimezone}
              instrumentTimezone={scenario.target.timezone}
            />
          </div>
          <div className="border-t p-3 text-right">
            <Button size="sm" variant="ghost" onClick={onRetry}>
              Check again
            </Button>
          </div>
        </>
      )}
    </Card>
  );
}

export function useFixturePreview(
  scenarioKey: CodexScenario,
  rule: CodexRule,
  formState: BookingFormState | undefined,
) {
  const scenario = codexScenarios[scenarioKey];
  const draft = formState?.draft ?? scenario.window;
  const result = useMemo(() => previewRows(scenario, rule, draft), [draft, rule, scenario]);
  const [phase, setPhase] = useState<PreviewPhase>(scenario.previewError ? "error" : "ready");
  const retry = () => {
    setPhase("checking");
    window.setTimeout(() => setPhase(scenario.previewError ? "error" : "ready"), 450);
  };
  return { draft, result, phase, retry };
}
