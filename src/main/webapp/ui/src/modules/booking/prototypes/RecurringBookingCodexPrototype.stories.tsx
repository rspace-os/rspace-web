// PROTOTYPE ONLY. Three Storybook concepts for the proposed recurring-booking flow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy is intentionally not entering the translation catalog. */

import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { AlertTriangleIcon, ArrowLeftIcon, CheckIcon, InfoIcon, Repeat2Icon } from "lucide-react";
import { HttpResponse, http } from "msw";
import { setupWorker } from "msw/browser";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryCard } from "@/modules/booking/components/BookingSummaryCard";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import type { BookingFormState, BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingForm } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import type { Booking, BookingListDocument } from "@/modules/booking/domain/booking";
import { formatBookingAgendaTimeRange } from "@/modules/booking/domain/bookingAgenda";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/common/ui/dialog";
import { FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Textarea } from "@/modules/common/ui/textarea";
import { Heading } from "@/modules/common/ui/typography";
import {
  type CodexOccurrence,
  type CodexRule,
  type CodexScenario,
  type CodexWeekday,
  codexScenarios,
  workspaceOccurrences,
} from "./recurringBookingCodexFixtures";

type Concept = "inline" | "review" | "workspace";
type PreviewPhase = "ready" | "checking" | "error";
type Inspection = Record<string, unknown>;
const emptyRows: readonly CodexOccurrence[] = [];
const prototypeDisplayTimezone = "Europe/London";
const cancellationReasonMaxLength = 500;

function bookingTarget(item: BookableItemOption): NonNullable<Booking["target"]> {
  return {
    relationTo: "booking-instruments",
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

const prototypeWorker = setupWorker(
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

const scenarioOptions: ReadonlyArray<{ key: CodexScenario; label: string }> = [
  { key: "ready", label: "Available" },
  { key: "daily", label: "Daily" },
  { key: "conflict", label: "Conflict" },
  { key: "dst", label: "DST shift" },
  { key: "maintenance", label: "Maintenance" },
  { key: "uncertain", label: "Uncertain save" },
  { key: "previewError", label: "Preview error" },
];

function formatDate(date: string): string {
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

function RepeatControls({
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

function PreviewPanel({
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

function useFixturePreview(scenarioKey: CodexScenario, rule: CodexRule, formState: BookingFormState | undefined) {
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

function BookingFormExample({
  scenarioKey,
  fixtureVersion,
  preview,
  rule,
  onRuleChange,
  onFormStateChange,
  onLoadFixture,
  allowReviewOnConflict = false,
  onReview,
  onSave,
  outcomeUncertain = false,
  pending = false,
  showPreview = true,
}: {
  scenarioKey: CodexScenario;
  fixtureVersion: number;
  preview: ReturnType<typeof useFixturePreview>;
  rule: CodexRule;
  onRuleChange: (rule: CodexRule) => void;
  onFormStateChange: (state: BookingFormState) => void;
  onLoadFixture: () => void;
  allowReviewOnConflict?: boolean;
  onReview?: () => void;
  onSave: (submission: BookingFormSubmission) => Promise<unknown>;
  outcomeUncertain?: boolean;
  pending?: boolean;
  showPreview?: boolean;
}) {
  const scenario = codexScenarios[scenarioKey];
  const formId = `review-booking-${scenarioKey}-${fixtureVersion}`;
  const formContainerRef = useRef<HTMLDivElement>(null);
  const draftBridge = useBookingTimelineDraft();
  const blockers = preview.result.kind !== "ready" || preview.phase !== "ready";
  const hasConflict =
    preview.result.kind === "ready" &&
    preview.result.rows.some((row) => row.status === "overlap" || row.status === "openingHours");
  const submitBlocked =
    blockers ||
    (!allowReviewOnConflict && hasConflict) ||
    draftBridge.adjustmentPending ||
    draftBridge.interactionActive ||
    outcomeUncertain;
  const currentDraft = draftBridge.draft ?? preview.draft;
  const updateFormState = (state: BookingFormState) => {
    draftBridge.onStateChange(state);
    onFormStateChange(state);
  };
  return (
    <div className="@container">
      <div className="grid gap-6 @4xl:grid-cols-[minmax(0,1fr)_30rem]">
        <div className="min-w-0 space-y-6">
          <section ref={formContainerRef} className="min-w-0 rounded-sm border bg-background p-5">
            <BookingForm
              key={`booking-form-${scenarioKey}-${fixtureVersion}`}
              formId={formId}
              mode="add"
              initialTarget={scenario.target}
              initialWindow={scenario.window}
              initialPurpose={
                scenario.eventKind === "MAINTENANCE" ? "Annual service inspection" : "Cell imaging protocol"
              }
              eventKind={scenario.eventKind}
              lockTarget
              token="storybook-prototype"
              pending={pending}
              error={preview.result.kind === "unsupported" ? "The changed example has no matching preview." : undefined}
              submissionBlocked={submitBlocked}
              outcomeUncertain={outcomeUncertain}
              displayTimezone={prototypeDisplayTimezone}
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={updateFormState}
              onDraftChange={draftBridge.onDraftChange}
              windowAdjustment={draftBridge.windowAdjustment}
              windowAdjustmentTarget={draftBridge.windowAdjustmentTarget}
              onCancel={() => undefined}
              onSubmit={async (submission) => {
                if (allowReviewOnConflict) onReview?.();
                else await onSave(submission);
              }}
              afterWindowFields={
                <RepeatControls rule={rule} anchorDate={preview.draft.startDate} onChange={onRuleChange} />
              }
            />
            {allowReviewOnConflict ? (
              <div className="mt-4 flex justify-end">
                <Button type="submit" form={formId} disabled={blockers || outcomeUncertain}>
                  Review occurrences
                </Button>
              </div>
            ) : null}
          </section>
          {showPreview ? (
            <PreviewPanel
              scenario={scenario}
              result={preview.result}
              phase={preview.phase}
              onRetry={preview.retry}
              onLoadFixture={onLoadFixture}
            />
          ) : preview.result.kind === "unsupported" ? (
            <Alert className="mt-4">
              <InfoIcon />
              <AlertTitle>Preview invalidated</AlertTitle>
              <AlertDescription>{preview.result.reason}</AlertDescription>
              <Button className="mt-2" variant="outline" onClick={onLoadFixture}>
                Load fixture
              </Button>
            </Alert>
          ) : preview.phase !== "ready" ? (
            <PreviewPanel
              scenario={scenario}
              result={preview.result}
              phase={preview.phase}
              onRetry={preview.retry}
              onLoadFixture={onLoadFixture}
            />
          ) : (
            <p className="mt-4 text-sm text-muted-foreground">The full dry-run response appears in the review step.</p>
          )}
        </div>
        <aside className="min-w-0 space-y-6">
          <div className="hidden @4xl:block">
            <BookingItemInformationCard
              item={scenario.target}
              displayTimezone={prototypeDisplayTimezone}
              date={currentDraft.startDate}
            />
          </div>
          <BookingDayTimelineAside
            formContainerRef={formContainerRef}
            target={scenario.target}
            draft={currentDraft}
            timezone={prototypeDisplayTimezone}
            token="storybook-prototype"
            onInteractionChange={draftBridge.onInteractionChange}
            onChange={(draft) => draftBridge.onTimelineChange(draft, scenario.target.globalId)}
          />
        </aside>
      </div>
    </div>
  );
}

function InlineConcept({
  scenarioKey,
  fixtureVersion,
  reportState,
  onLoadFixture,
}: {
  scenarioKey: CodexScenario;
  fixtureVersion: number;
  reportState: (state: Inspection) => void;
  onLoadFixture: () => void;
}) {
  const scenario = codexScenarios[scenarioKey];
  const [formState, setFormState] = useState<BookingFormState>();
  const [rule, setRule] = useState(scenario.rule);
  const preview = useFixturePreview(scenarioKey, rule, formState);
  const conflict =
    preview.result.kind === "ready" &&
    preview.result.rows.some((row) => row.status === "overlap" || row.status === "openingHours");
  const blocked = preview.result.kind !== "ready" || preview.phase !== "ready" || conflict;
  const [saveState, setSaveState] = useState<"idle" | "saving" | "success" | "uncertain">("idle");
  const [existingChecked, setExistingChecked] = useState(false);
  const rows = preview.result.kind === "ready" ? preview.result.rows : emptyRows;
  const save = async (_submission: BookingFormSubmission) => {
    if (blocked) return;
    setSaveState("saving");
    await new Promise((resolve) => window.setTimeout(resolve, 400));
    setSaveState(scenario.saveUncertain ? "uncertain" : "success");
  };
  useEffect(
    () => reportState({ concept: "inline", draft: preview.draft, rule, preview: rows, saveState }),
    [preview.draft, reportState, rule, rows, saveState],
  );
  return (
    <main className="mx-auto max-w-7xl space-y-5 p-5 lg:p-8">
      <div>
        <p className="text-sm text-muted-foreground">Concept 1 · repeat and preview stay in the normal creation flow</p>
        <Heading level={3} as="h2">
          Add booking
        </Heading>
      </div>
      {saveState === "success" ? (
        <Alert>
          <CheckIcon />
          <AlertTitle>Series created</AlertTitle>
          <AlertDescription>
            {rows.length} bookings were simulated; the first is {rows[0] ? formatDate(rows[0].date) : "not available"}.
          </AlertDescription>
        </Alert>
      ) : null}
      {saveState === "uncertain" ? (
        <Alert variant="destructive">
          <AlertTriangleIcon />
          <AlertTitle>Save outcome uncertain</AlertTitle>
          <AlertDescription>
            The request may have committed. Retry is blocked. Check existing bookings before trying again.
            <Button className="ml-2 h-auto p-0 align-baseline" variant="link" onClick={() => setExistingChecked(true)}>
              Check My Bookings
            </Button>
            {existingChecked ? (
              <span className="ml-2 block">Prototype check only: no backend was queried; retry remains blocked.</span>
            ) : null}
          </AlertDescription>
        </Alert>
      ) : null}
      <BookingFormExample
        key={`inline-form-${scenarioKey}-${fixtureVersion}`}
        scenarioKey={scenarioKey}
        fixtureVersion={fixtureVersion}
        preview={preview}
        rule={rule}
        onRuleChange={setRule}
        onFormStateChange={setFormState}
        onLoadFixture={onLoadFixture}
        onSave={save}
        outcomeUncertain={saveState === "uncertain"}
        pending={saveState === "saving"}
      />
    </main>
  );
}

function ReviewConcept({
  scenarioKey,
  fixtureVersion,
  reportState,
  onLoadFixture,
}: {
  scenarioKey: CodexScenario;
  fixtureVersion: number;
  reportState: (state: Inspection) => void;
  onLoadFixture: () => void;
}) {
  const scenario = codexScenarios[scenarioKey];
  const [formState, setFormState] = useState<BookingFormState>();
  const [rule, setRule] = useState(scenario.rule);
  const preview = useFixturePreview(scenarioKey, rule, formState);
  const [reviewing, setReviewing] = useState(false);
  const [saveState, setSaveState] = useState<"idle" | "success" | "uncertain">("idle");
  const [existingChecked, setExistingChecked] = useState(false);
  const rows = preview.result.kind === "ready" ? preview.result.rows : emptyRows;
  const conflicts = rows.some((row) => row.status === "overlap" || row.status === "openingHours");
  const saveBlocked =
    preview.result.kind !== "ready" || preview.phase !== "ready" || conflicts || saveState === "uncertain";
  useEffect(
    () =>
      reportState({
        concept: "review",
        step: reviewing ? "review" : "details",
        draft: preview.draft,
        rule,
        preview: rows,
        saveState,
      }),
    [preview.draft, reportState, reviewing, rule, rows, saveState],
  );
  return (
    <main className="mx-auto max-w-6xl space-y-5 p-5 lg:p-8">
      <div>
        <p className="text-sm text-muted-foreground">Concept 2 · split the workflow into details and review</p>
        <Heading level={3} as="h2">
          {reviewing ? "Review recurring booking" : "Booking details"}
        </Heading>
        <div className="mt-3 flex items-center gap-2 text-sm">
          <Badge variant={reviewing ? "secondary" : "default"}>1 · Details</Badge>
          <span className="text-muted-foreground">→</span>
          <Badge variant={reviewing ? "default" : "outline"}>2 · Review & save</Badge>
        </div>
      </div>
      <div className={reviewing ? "hidden" : ""}>
        <BookingFormExample
          key={`review-form-${scenarioKey}-${fixtureVersion}`}
          scenarioKey={scenarioKey}
          fixtureVersion={fixtureVersion}
          preview={preview}
          rule={rule}
          onRuleChange={setRule}
          onFormStateChange={setFormState}
          onLoadFixture={onLoadFixture}
          allowReviewOnConflict
          onReview={() => setReviewing(true)}
          onSave={async () => undefined}
          showPreview={false}
        />
      </div>
      {reviewing ? (
        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <PreviewPanel
            scenario={scenario}
            result={preview.result}
            phase={preview.phase}
            onRetry={preview.retry}
            onLoadFixture={onLoadFixture}
          />
          <aside className="space-y-4">
            <Card size="sm">
              <CardHeader>
                <CardTitle className="text-base">Series summary</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <InventoryItem name={scenario.target.name} globalId={scenario.target.globalId} size="sm" />
                <div className="flex flex-wrap gap-2">
                  <Badge variant="outline">{scenario.eventKind}</Badge>
                  <Badge variant="outline">{scenario.target.timezone}</Badge>
                </div>
                <p className="text-sm">
                  {rows.length} occurrences · {rows[0] ? formatDate(rows[0].date) : "—"} first
                </p>
                {conflicts ? (
                  <p className="text-sm text-destructive">Save all-or-nothing is blocked by the listed conflict.</p>
                ) : (
                  <p className="text-sm text-muted-foreground">The server checks every row again when saved.</p>
                )}
              </CardContent>
            </Card>
            {saveState === "success" ? (
              <Alert>
                <CheckIcon />
                <AlertTitle>Series created</AlertTitle>
                <AlertDescription>
                  {rows.length} bookings; first on {rows[0] ? formatDate(rows[0].date) : "—"}.
                </AlertDescription>
              </Alert>
            ) : null}
            {saveState === "uncertain" ? (
              <Alert variant="destructive">
                <AlertTriangleIcon />
                <AlertTitle>Save outcome uncertain</AlertTitle>
                <AlertDescription>
                  Retry is blocked. Check existing bookings before trying again.
                  <Button className="mt-2 h-auto p-0" variant="link" onClick={() => setExistingChecked(true)}>
                    Check My Bookings
                  </Button>
                  {existingChecked ? (
                    <span className="mt-1 block">
                      Prototype check only: no backend was queried; retry remains blocked.
                    </span>
                  ) : null}
                </AlertDescription>
              </Alert>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setReviewing(false)}>
                <ArrowLeftIcon /> Back to details
              </Button>
              <Button
                disabled={saveBlocked}
                onClick={() => setSaveState(scenario.saveUncertain ? "uncertain" : "success")}
              >
                Save series
              </Button>
            </div>
          </aside>
        </div>
      ) : null}
    </main>
  );
}

type WorkspaceRow = (typeof workspaceOccurrences)[number];
type EditScope = "THIS" | "FOLLOWING";
type CancelScope = EditScope | "SERIES";

function workspaceDocument(row: WorkspaceRow): BookingListDocument {
  return {
    id: row.id,
    version: 2,
    target: {
      relationTo: "booking-instruments",
      value: {
        id: 123,
        name: "Confocal microscope",
        deleted: false,
        parentContainerName: "Imaging lab",
        parentContainerGlobalId: "IC456",
      },
      globalId: "IN123",
    },
    canViewConfiguration: true,
    timezone: "Europe/London",
    start: row.startInstant,
    end: row.endInstant,
    state: row.cancelled ? "CANCELLED" : "CONFIRMED",
    kind: "BOOKING",
    requesterId: 84,
    purpose: "Cell imaging protocol",
    bookedBy: "Ada Lovelace (ada)",
    createdBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: !row.isPast && !row.cancelled,
    canCancel: !row.isPast && !row.cancelled,
    createdAt: "2026-08-01T09:00:00Z",
    updatedAt: "2026-08-02T10:00:00Z",
  };
}

function actionRows(scope: CancelScope, selected: WorkspaceRow): WorkspaceRow[] {
  return workspaceOccurrences.filter((row) => {
    if (row.isPast || row.cancelled) return false;
    if (scope === "THIS") return row.id === selected.id;
    if (scope === "FOLLOWING") return row.position >= selected.position;
    return true;
  });
}

function rangeLabel(rows: readonly WorkspaceRow[]): string {
  if (!rows.length) return "No confirmed future bookings are affected.";
  return `${rows.length} confirmed booking${rows.length === 1 ? "" : "s"} · ${formatDate(rows[0].date)} to ${formatDate(rows[rows.length - 1].date)}`;
}

function ScopeDialog({
  open,
  onOpenChange,
  mode,
  selected,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "edit" | "cancel";
  selected: WorkspaceRow;
  onConfirm: (summary: string) => void;
}) {
  const [scope, setScope] = useState<CancelScope>("THIS");
  const [reason, setReason] = useState("");
  const reasonId = useId();
  const wasOpen = useRef(false);
  const rows = actionRows(scope, selected);
  const isCancel = mode === "cancel";
  useEffect(() => {
    if (open && !wasOpen.current) {
      setScope("THIS");
      setReason("");
    }
    wasOpen.current = open;
  }, [open]);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isCancel ? "Cancel booking" : "Edit booking"} · #{selected.position} of 12
          </DialogTitle>
          <DialogDescription>Choose which confirmed future occurrences this action covers.</DialogDescription>
        </DialogHeader>
        <RadioGroup
          aria-label="Series action scope"
          value={scope}
          onValueChange={(value) => setScope(value as CancelScope)}
          className="gap-3"
        >
          <Label className="flex items-start gap-3 rounded-sm border p-3">
            <RadioGroupItem value="THIS" className="mt-0.5" />
            <span>
              <span className="font-medium">This booking</span>
              <span className="mt-1 block text-sm text-muted-foreground">
                {rangeLabel(actionRows("THIS", selected))}
              </span>
            </span>
          </Label>
          <Label className="flex items-start gap-3 rounded-sm border p-3">
            <RadioGroupItem value="FOLLOWING" className="mt-0.5" />
            <span>
              <span className="font-medium">This and following</span>
              <span className="mt-1 block text-sm text-muted-foreground">
                {rangeLabel(actionRows("FOLLOWING", selected))}
              </span>
            </span>
          </Label>
          {isCancel ? (
            <Label className="flex items-start gap-3 rounded-sm border p-3">
              <RadioGroupItem value="SERIES" className="mt-0.5" />
              <span>
                <span className="font-medium">All remaining in series</span>
                <span className="mt-1 block text-sm text-muted-foreground">
                  {rangeLabel(actionRows("SERIES", selected))}
                </span>
              </span>
            </Label>
          ) : null}
        </RadioGroup>
        {isCancel ? (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">Past and already cancelled bookings are excluded.</p>
            <div className="space-y-2">
              <Label htmlFor={reasonId}>Cancellation reason (optional)</Label>
              <Textarea
                id={reasonId}
                value={reason}
                maxLength={cancellationReasonMaxLength}
                rows={3}
                aria-describedby={`${reasonId}-count`}
                onChange={(event) => setReason(event.currentTarget.value)}
              />
              <p id={`${reasonId}-count`} className="text-right text-xs text-muted-foreground" aria-live="polite">
                {reason.length}/{cancellationReasonMaxLength}
              </p>
            </div>
          </div>
        ) : null}
        {!isCancel && scope === "FOLLOWING" ? (
          <Alert>
            <InfoIcon />
            <AlertTitle>Following bookings will be recreated</AlertTitle>
            <AlertDescription>
              Any earlier moved occurrence in this range is replaced by the new series.
            </AlertDescription>
          </Alert>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button
            variant={isCancel ? "destructive" : "default"}
            disabled={rows.length === 0}
            onClick={() =>
              onConfirm(
                `${isCancel ? "Cancellation" : "Edit"} scope selected · ${rangeLabel(rows)}${isCancel && reason.trim() ? ` · Reason: ${reason.trim()}` : ""} · no changes applied in this prototype`,
              )
            }
          >
            {isCancel ? "Review cancellation scope" : "Continue to edit"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function WorkspaceConcept({ reportState }: { reportState: (state: Inspection) => void }) {
  const [selectedPosition, setSelectedPosition] = useState(6);
  const [editOpen, setEditOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [actionNotice, setActionNotice] = useState("");
  const selected = workspaceOccurrences.find((row) => row.position === selectedPosition) ?? workspaceOccurrences[5];
  useEffect(
    () => reportState({ concept: "workspace", selectedPosition, editOpen, cancelOpen, actionNotice }),
    [actionNotice, cancelOpen, editOpen, reportState, selectedPosition],
  );
  return (
    <main className="mx-auto max-w-7xl space-y-5 p-5 lg:p-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">Concept 3 · manage one series as a workspace</p>
          <Heading level={3} as="h2">
            Recurring bookings
          </Heading>
        </div>
        <Badge variant="outline">12 occurrences · Weekly on Mondays · Europe/London</Badge>
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,0.7fr)]">
        <Card size="sm" className="gap-0 py-0">
          <CardHeader className="border-b py-4">
            <CardTitle className="text-base">Confocal microscope · Cell imaging protocol</CardTitle>
            <p className="text-xs text-muted-foreground">Position labels stay stable when one occurrence is moved.</p>
          </CardHeader>
          <div className="[&_td]:whitespace-normal [&_th]:whitespace-normal">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Position</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Time</TableHead>
                  <TableHead>State</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {workspaceOccurrences.map((row) => (
                  <TableRow
                    key={row.id}
                    data-state={row.position === selected.position ? "selected" : undefined}
                    className={row.position === selected.position ? "bg-muted" : ""}
                  >
                    <TableCell>
                      <Button
                        variant="link"
                        size="sm"
                        className="h-auto p-0"
                        aria-pressed={row.position === selected.position}
                        onClick={() => setSelectedPosition(row.position)}
                      >
                        {row.position} of 12
                      </Button>
                    </TableCell>
                    <TableCell>{formatDate(row.date)}</TableCell>
                    <TableCell>
                      {row.start}–{row.end}
                    </TableCell>
                    <TableCell>
                      {row.cancelled ? (
                        <Badge variant="secondary">Cancelled</Badge>
                      ) : row.isPast ? (
                        <Badge variant="outline">Past</Badge>
                      ) : row.moved ? (
                        <Badge variant="outline">Moved</Badge>
                      ) : (
                        <Badge variant="secondary">Confirmed</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Card>
        <aside className="space-y-4">
          <div className="flex items-center gap-2">
            <h3 className="font-medium">Occurrence detail</h3>
            <Badge variant="outline">{selected.position} of 12</Badge>
          </div>
          <BookingSummaryCard booking={workspaceDocument(selected)} timeZone="Europe/London" />
          <Card size="sm">
            <CardHeader>
              <CardTitle className="text-base">Series facts</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p>
                <span className="text-muted-foreground">Repeat:</span> Weekly on Mondays
              </p>
              <p>
                <span className="text-muted-foreground">Selected:</span> {formatDate(selected.date)} · 10:00–11:30
              </p>
              {selected.moved ? (
                <p className="text-muted-foreground">This occurrence was moved from its original time.</p>
              ) : null}
              <p className="text-xs text-muted-foreground">
                Past and cancelled occurrences remain visible in history and are omitted from scope counts.
              </p>
            </CardContent>
          </Card>
          {actionNotice ? (
            <Alert>
              <InfoIcon />
              <AlertTitle>Prototype scope selection</AlertTitle>
              <AlertDescription>{actionNotice}</AlertDescription>
            </Alert>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              disabled={selected.isPast || selected.cancelled}
              onClick={() => {
                setActionNotice("");
                setEditOpen(true);
              }}
            >
              Edit scope
            </Button>
            <Button
              variant="destructive"
              disabled={selected.isPast || selected.cancelled}
              onClick={() => {
                setActionNotice("");
                setCancelOpen(true);
              }}
            >
              Cancel scope
            </Button>
          </div>
        </aside>
      </div>
      <ScopeDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        mode="edit"
        selected={selected}
        onConfirm={(notice) => {
          setEditOpen(false);
          setActionNotice(notice);
        }}
      />
      <ScopeDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        mode="cancel"
        selected={selected}
        onConfirm={(notice) => {
          setCancelOpen(false);
          setActionNotice(notice);
        }}
      />
    </main>
  );
}

function PrototypeLab({
  initialConcept = "inline",
  initialScenario = "ready",
}: {
  initialConcept?: Concept;
  initialScenario?: CodexScenario;
}) {
  const [concept, setConcept] = useState<Concept>(initialConcept);
  const [scenario, setScenario] = useState<CodexScenario>(initialScenario);
  const [fixtureVersion, setFixtureVersion] = useState(0);
  const [inspection, setInspection] = useState<Inspection>({});
  const selectedScenario = codexScenarios[scenario];
  const reloadFixture = () => setFixtureVersion((version) => version + 1);
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <div className="min-h-screen bg-background text-foreground">
        <div className="border-b-4 border-violet-600 bg-slate-950 px-5 py-4 text-white shadow-md lg:px-8">
          <div className="mx-auto max-w-7xl space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <Badge className="bg-violet-700 text-white hover:bg-violet-700">CODEX · PROTOTYPE</Badge>
                <div>
                  <p className="font-semibold">Recurring booking concepts</p>
                  <p className="text-xs text-slate-300">Storybook only · local in-memory interactions</p>
                </div>
              </div>
              <p className="max-w-2xl text-xs text-slate-300">
                Fixture limitation: no backend calls. Editing a rule or window invalidates the preview until you load an
                example.
              </p>
            </div>
            <fieldset className="flex flex-wrap items-center gap-2">
              <legend className="sr-only">Prototype concept and scenario</legend>
              <span className="mr-1 text-xs font-semibold uppercase tracking-wide text-slate-300">Concept</span>
              {(["inline", "review", "workspace"] as const).map((value, index) => (
                <Button
                  key={value}
                  size="sm"
                  variant={concept === value ? "default" : "outline"}
                  className={
                    concept === value
                      ? "bg-violet-700 text-white hover:bg-violet-800"
                      : "border-slate-600 bg-slate-900 text-white hover:bg-slate-800"
                  }
                  onClick={() => {
                    setConcept(value);
                    reloadFixture();
                  }}
                >
                  {index + 1} ·{" "}
                  {value === "inline" ? "Inline" : value === "review" ? "Review step" : "Series workspace"}
                </Button>
              ))}
              {concept === "workspace" ? (
                <span className="ml-3 rounded-sm border border-slate-600 px-3 py-1.5 text-xs text-slate-200">
                  Workspace fixture · weekly series
                </span>
              ) : (
                <>
                  <span className="ml-3 mr-1 text-xs font-semibold uppercase tracking-wide text-slate-300">
                    Scenario
                  </span>
                  {scenarioOptions.map((option) => (
                    <Button
                      key={option.key}
                      size="sm"
                      variant={scenario === option.key ? "secondary" : "ghost"}
                      className={
                        scenario === option.key
                          ? "bg-violet-200 text-slate-950 hover:bg-violet-200"
                          : "text-white hover:bg-slate-800 hover:text-white"
                      }
                      onClick={() => {
                        setScenario(option.key);
                        reloadFixture();
                      }}
                    >
                      {option.label}
                    </Button>
                  ))}
                </>
              )}
            </fieldset>
            <p className="text-xs text-slate-300">
              Provisional v1 controls: daily or weekly · interval 1–4 · count 2–52 or end date within one year ·
              weekdays include the first booking. Current fixture:{" "}
              <span className="font-medium text-white">
                {concept === "workspace"
                  ? "a separate series with fixed Monday occurrences"
                  : selectedScenario.description}
              </span>
            </p>
          </div>
        </div>
        <div key={`${concept}-${scenario}-${fixtureVersion}`}>
          {concept === "inline" ? (
            <InlineConcept
              scenarioKey={scenario}
              fixtureVersion={fixtureVersion}
              reportState={setInspection}
              onLoadFixture={reloadFixture}
            />
          ) : concept === "review" ? (
            <ReviewConcept
              scenarioKey={scenario}
              fixtureVersion={fixtureVersion}
              reportState={setInspection}
              onLoadFixture={reloadFixture}
            />
          ) : (
            <WorkspaceConcept reportState={setInspection} />
          )}
        </div>
        <div className="mx-auto max-w-7xl px-5 pb-8 lg:px-8">
          <details className="rounded-sm border border-dashed bg-muted/20 p-3 text-xs">
            <summary className="cursor-pointer font-medium">Prototype state inspector · not product UI</summary>
            <pre className="mt-3 overflow-auto whitespace-pre-wrap text-[11px]">
              {JSON.stringify({ concept, scenario, ...inspection }, null, 2)}
            </pre>
          </details>
        </div>
      </div>
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Recurring Bookings (Codex)",
  component: PrototypeLab,
  parameters: { layout: "fullscreen" },
  beforeEach: async () => {
    await prototypeWorker.start({ onUnhandledRequest: "bypass" });
    return () => prototypeWorker.stop();
  },
} satisfies Meta<typeof PrototypeLab>;

export default meta;
type Story = StoryObj<typeof meta>;

export const InlineRepeat: Story = { args: { initialConcept: "inline", initialScenario: "ready" } };
export const TwoStepReview: Story = { args: { initialConcept: "review", initialScenario: "ready" } };
export const DailySeries: Story = { args: { initialConcept: "inline", initialScenario: "daily" } };
export const SeriesWorkspace: Story = { args: { initialConcept: "workspace", initialScenario: "ready" } };
export const ConflictBlocked: Story = { args: { initialConcept: "inline", initialScenario: "conflict" } };
export const DSTShiftWarning: Story = { args: { initialConcept: "inline", initialScenario: "dst" } };
export const MaintenanceSeries: Story = { args: { initialConcept: "inline", initialScenario: "maintenance" } };
export const UncertainSave: Story = { args: { initialConcept: "review", initialScenario: "uncertain" } };
export const PreviewError: Story = { args: { initialConcept: "inline", initialScenario: "previewError" } };
