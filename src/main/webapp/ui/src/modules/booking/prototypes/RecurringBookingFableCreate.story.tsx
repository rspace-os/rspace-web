// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { Temporal } from "@js-temporal/polyfill";
import { AlertTriangleIcon, InfoIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type BookingWindowDraft, bookingHourCycle, isPlainDate } from "@/modules/booking/domain/bookingTime";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { FieldError, FieldLegend, FieldSet } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Heading } from "@/modules/common/ui/typography";
import {
  CONFIGURATION_TIMEZONE,
  clamp,
  DATE,
  DISPLAY_TIMEZONE,
  describeRule,
  dryRun,
  firstWeekday,
  INITIAL_WINDOW,
  item,
  MAX_OCCURRENCES,
  type Occurrence,
  type RecurrenceRule,
  ruleProblem,
  WEEKDAYS,
} from "./RecurringBookingFableModel";

export function useOccurrenceFormat() {
  const { i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const language = i18n.resolvedLanguage ?? i18n.language;
  return React.useMemo(() => {
    const date = new Intl.DateTimeFormat(language, {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
      timeZone: DISPLAY_TIMEZONE,
    });
    const time = new Intl.DateTimeFormat(language, {
      timeStyle: "short",
      hourCycle: bookingHourCycle(timeFormat),
      timeZone: DISPLAY_TIMEZONE,
    });
    return {
      date: (instant: string) => date.format(new Date(instant)),
      period: (start: string, end: string) => time.formatRange(new Date(start), new Date(end)),
    };
  }, [language, timeFormat]);
}

// ---------------------------------------------------------------------------
// Repeat control, rendered inside BookingForm through its `afterWindowFields` slot.
// ---------------------------------------------------------------------------

export function RepeatPanel({
  rule,
  draft,
  problem,
  onChange,
}: {
  rule: RecurrenceRule;
  draft: BookingWindowDraft;
  problem?: string;
  onChange: (next: RecurrenceRule) => void;
}) {
  const id = React.useId();
  const weekly = rule.frequency === "WEEKLY";
  const repeating = rule.frequency !== "NONE";
  const anchor = firstWeekday(draft);
  const first = isPlainDate(draft.startDate) ? Temporal.PlainDate.from(draft.startDate) : undefined;
  const defaultUntil = (first ?? Temporal.PlainDate.from(DATE)).add({ weeks: 10 }).toString();
  return (
    <FieldSet className="gap-4">
      <FieldLegend variant="label">Repeat</FieldLegend>
      <RadioGroup
        aria-label="Repeat"
        value={rule.frequency}
        onValueChange={(value) => onChange({ ...rule, frequency: String(value) as RecurrenceRule["frequency"] })}
        className="flex flex-wrap gap-4"
      >
        {(["NONE", "DAILY", "WEEKLY"] as const).map((frequency) => (
          <div key={frequency} className="flex items-center gap-2">
            <RadioGroupItem value={frequency} id={`${id}-${frequency}`} />
            <Label htmlFor={`${id}-${frequency}`}>
              {frequency === "NONE" ? "Does not repeat" : frequency === "DAILY" ? "Daily" : "Weekly"}
            </Label>
          </div>
        ))}
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
              onChange={(event) => onChange({ ...rule, interval: clamp(Number(event.currentTarget.value) || 1, 1, 4) })}
            />
            <span>{weekly ? (rule.interval === 1 ? "week" : "weeks") : rule.interval === 1 ? "day" : "days"}</span>
          </div>
          {weekly ? (
            <FieldSet className="gap-2">
              <FieldLegend variant="label" className="mb-1">
                On
              </FieldLegend>
              <div className="flex flex-wrap gap-3">
                {WEEKDAYS.map((day) => {
                  const anchored = day.value === anchor;
                  const checked = anchored || rule.weekdays.includes(day.value);
                  return (
                    <label
                      key={day.value}
                      htmlFor={`${id}-day-${day.value}`}
                      className="flex items-center gap-1.5 text-sm"
                    >
                      <Checkbox
                        id={`${id}-day-${day.value}`}
                        checked={checked}
                        disabled={anchored}
                        aria-label={anchored ? `${day.label} (first occurrence)` : day.label}
                        onCheckedChange={(next) =>
                          onChange({
                            ...rule,
                            weekdays: next
                              ? [...rule.weekdays, day.value].sort((a, b) => a - b)
                              : rule.weekdays.filter((value) => value !== day.value),
                          })
                        }
                      />
                      <span aria-hidden="true">{day.short}</span>
                    </label>
                  );
                })}
              </div>
            </FieldSet>
          ) : null}
          <FieldSet className="gap-2">
            <FieldLegend variant="label" className="mb-1">
              Ends
            </FieldLegend>
            <RadioGroup
              aria-label="Ends"
              value={rule.ends.kind}
              onValueChange={(value) =>
                onChange({
                  ...rule,
                  ends: value === "until" ? { kind: "until", until: defaultUntil } : { kind: "count", count: 12 },
                })
              }
              className="flex flex-col gap-2"
            >
              <div className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="count" id={`${id}-ends-count`} />
                <Label htmlFor={`${id}-ends-count`}>After</Label>
                <Input
                  type="number"
                  min={2}
                  max={MAX_OCCURRENCES}
                  className="w-20"
                  aria-label="Number of occurrences"
                  disabled={rule.ends.kind !== "count"}
                  value={rule.ends.kind === "count" ? rule.ends.count : 12}
                  onChange={(event) =>
                    onChange({ ...rule, ends: { kind: "count", count: Number(event.currentTarget.value) || 0 } })
                  }
                />
                <span>occurrences</span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="until" id={`${id}-ends-until`} />
                <Label htmlFor={`${id}-ends-until`}>On</Label>
                <Input
                  type="date"
                  className="w-44"
                  aria-label="Last occurrence date"
                  disabled={rule.ends.kind !== "until"}
                  min={first?.toString()}
                  max={first?.add({ years: 1 }).toString()}
                  value={rule.ends.kind === "until" ? rule.ends.until : ""}
                  onChange={(event) => onChange({ ...rule, ends: { kind: "until", until: event.currentTarget.value } })}
                />
              </div>
            </RadioGroup>
            {problem ? <FieldError>{problem}</FieldError> : null}
          </FieldSet>
          <p className="text-xs text-muted-foreground">
            Repeats at the same time in {DISPLAY_TIMEZONE}. Up to {MAX_OCCURRENCES} occurrences within one year.
          </p>
        </>
      ) : null}
    </FieldSet>
  );
}

// ---------------------------------------------------------------------------
// Occurrence preview, rendered from the (simulated) dry-run response.
// ---------------------------------------------------------------------------

function OccurrenceStatusBadge({ occurrence }: { occurrence: Occurrence }) {
  return (
    <span className="flex flex-wrap items-center gap-1">
      {occurrence.status === "ok" ? (
        <Badge variant="secondary">Available</Badge>
      ) : occurrence.status === "overlap" ? (
        <Badge variant="destructive">Overlap</Badge>
      ) : (
        <Badge variant="destructive">Closed</Badge>
      )}
      {occurrence.dstShifted ? <Badge variant="outline">Time shifted</Badge> : null}
      {occurrence.conflictWith ? (
        <span className="block w-full text-xs text-muted-foreground">{occurrence.conflictWith}</span>
      ) : null}
    </span>
  );
}

export function OccurrencePreview({
  rule,
  draft,
  occurrences,
  problem,
}: {
  rule: RecurrenceRule;
  draft: BookingWindowDraft;
  occurrences: readonly Occurrence[];
  problem?: string;
}) {
  const format = useOccurrenceFormat();
  if (rule.frequency === "NONE") return null;
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  const last = occurrences[occurrences.length - 1];
  return (
    <Card size="sm" className="gap-0 py-0">
      <CardHeader className="border-b py-4">
        <CardTitle className="text-base">Occurrences</CardTitle>
        <p className="text-xs text-muted-foreground">
          {describeRule(rule, firstWeekday(draft))}
          {last ? ` · ${occurrences.length} occurrences · until ${format.date(last.start)}` : ""}
          {` · times in ${DISPLAY_TIMEZONE}`}
        </p>
      </CardHeader>
      <CardContent className="p-0">
        {problem ? (
          <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
            <AlertTriangleIcon />
            <AlertTitle>{problem}</AlertTitle>
          </Alert>
        ) : occurrences.length === 0 ? (
          <Alert className="rounded-none border-x-0 border-t-0">
            <InfoIcon />
            <AlertTitle>Enter a valid date and time to preview the series.</AlertTitle>
          </Alert>
        ) : conflicts.length > 0 ? (
          <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
            <AlertTriangleIcon />
            <AlertTitle>
              {conflicts.length} of {occurrences.length} occurrences cannot be booked
            </AlertTitle>
            <AlertDescription>Every occurrence must be free before the series can be saved.</AlertDescription>
          </Alert>
        ) : (
          <Alert className="rounded-none border-x-0 border-t-0">
            <InfoIcon />
            <AlertTitle>All {occurrences.length} occurrences are available</AlertTitle>
            <AlertDescription>Each occurrence is checked again when you save.</AlertDescription>
          </Alert>
        )}
        {occurrences.length > 0 && !problem ? (
          // Cells wrap so the table never scrolls: axe requires scrollable regions to be focusable.
          <div className="[&_td]:whitespace-normal [&_th]:whitespace-normal">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8">#</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="w-28">Time</TableHead>
                  <TableHead className="w-32">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {occurrences.map((occurrence) => (
                  <TableRow
                    key={occurrence.position}
                    className={occurrence.status !== "ok" ? "bg-destructive/5" : undefined}
                  >
                    <TableCell className="text-muted-foreground">{occurrence.position}</TableCell>
                    <TableCell>{format.date(occurrence.start)}</TableCell>
                    <TableCell>
                      <BookingInstrumentTimeTooltip
                        start={occurrence.start}
                        end={occurrence.end}
                        displayTimeZone={DISPLAY_TIMEZONE}
                        instrumentTimeZone={CONFIGURATION_TIMEZONE}
                      >
                        <time dateTime={occurrence.start}>{format.period(occurrence.start, occurrence.end)}</time>
                      </BookingInstrumentTimeTooltip>
                    </TableCell>
                    <TableCell>
                      <OccurrenceStatusBadge occurrence={occurrence} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Create story: the production Add Booking page (form, item card, day timeline) plus Repeat and preview.
// ---------------------------------------------------------------------------

export function CreateRecurringPrototype({ initialRule }: { initialRule: RecurrenceRule }) {
  const { t } = useTranslation("booking");
  const format = useOccurrenceFormat();
  const [rule, setRule] = React.useState(initialRule);
  const [saved, setSaved] = React.useState<string>();
  const bridge = useBookingTimelineDraft();
  const formContainerRef = React.useRef<HTMLDivElement>(null);
  const formState = bridge.formState;
  const draft = bridge.draft ?? INITIAL_WINDOW;
  // Only a window that passes the item's rules is expanded, as only a valid window reaches the server.
  const occurrences = React.useMemo(() => (formState?.window ? dryRun(rule, formState.draft) : []), [rule, formState]);
  const problem = ruleProblem(rule, draft);
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  const repeating = rule.frequency !== "NONE";
  const blocked = problem !== undefined || conflicts.length > 0;
  const onStateChange = React.useCallback(
    (state: BookingFormState) => {
      bridge.onStateChange(state);
      setSaved(undefined);
    },
    [bridge.onStateChange],
  );
  const submit = async (_submission: BookingFormSubmission) => {
    await new Promise((resolve) => setTimeout(resolve, 300));
    setSaved(
      repeating
        ? `Series created: ${occurrences.length} bookings, first on ${format.date(occurrences[0].start)}.`
        : "Booking created.",
    );
  };

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      <div className="@container">
        <div className="grid gap-6 @4xl:grid-cols-[minmax(0,1fr)_30rem]">
          <div ref={formContainerRef} className="min-w-0 space-y-6">
            <BookingForm
              mode="add"
              displayTimezone={DISPLAY_TIMEZONE}
              eventKind="BOOKING"
              initialTarget={item}
              initialDate={DATE}
              initialWindow={INITIAL_WINDOW}
              token={OAUTH_TOKEN}
              pending={false}
              // Locked so the item picker never queries the API; production keeps the picker.
              lockTarget
              error={
                bridge.interactionActive
                  ? undefined
                  : (problem ??
                    (conflicts.length === 0
                      ? undefined
                      : repeating
                        ? `${conflicts.length} of ${occurrences.length} occurrences cannot be booked.`
                        : t("bookings.errors.overlap")))
              }
              submissionBlocked={bridge.adjustmentPending || bridge.interactionActive || blocked}
              showMobileItemInformation
              showRulesSummary={false}
              onStateChange={onStateChange}
              onDraftChange={bridge.onDraftChange}
              onTargetChange={bridge.onTargetChange}
              windowAdjustment={bridge.windowAdjustment}
              windowAdjustmentTarget={bridge.windowAdjustmentTarget}
              afterWindowFields={
                <RepeatPanel
                  rule={rule}
                  draft={draft}
                  problem={problem}
                  onChange={(next) => {
                    setRule(next);
                    setSaved(undefined);
                  }}
                />
              }
              onSubmit={submit}
            />
            <OccurrencePreview rule={rule} draft={draft} occurrences={occurrences} problem={problem} />
            {saved ? (
              <p className="text-sm" role="status">
                {saved}
              </p>
            ) : null}
          </div>
          <div className="min-w-0 space-y-6">
            <div className="hidden @4xl:block">
              <BookingItemInformationCard item={item} displayTimezone={DISPLAY_TIMEZONE} date={draft.startDate} />
            </div>
            <BookingDayTimelineAside
              formContainerRef={formContainerRef}
              target={item}
              draft={draft}
              timezone={DISPLAY_TIMEZONE}
              token={OAUTH_TOKEN}
              onInteractionChange={bridge.onInteractionChange}
              onChange={(next) => bridge.onTimelineChange(next, item.globalId)}
            />
          </div>
        </div>
      </div>
    </main>
  );
}
