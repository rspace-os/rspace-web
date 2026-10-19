// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { CheckIcon, Repeat2Icon, XIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import type { Booking } from "@/modules/booking/domain/booking";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatBookingDateTime } from "@/modules/booking/domain/bookingTime";
import { Panel } from "@/modules/booking/pages/bookings/BookingEventContext";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/modules/common/ui/alert-dialog";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Textarea } from "@/modules/common/ui/textarea";
import { Heading } from "@/modules/common/ui/typography";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { OccurrencePreview, RepeatPanel, useOccurrenceFormat } from "./RecurringBookingFableCreate.story";
import {
  affectedCount,
  CANCELLATION_REASON_MAX_LENGTH,
  currentBooking,
  DISPLAY_TIMEZONE,
  describeRule,
  dryRun,
  editableBooking,
  INITIAL_WINDOW,
  item,
  lastBooking,
  type RecurrenceRule,
  ruleProblem,
  type Scope,
  type SeriesMembership,
  STORED_RULE,
  sameRule,
  seriesOf,
} from "./RecurringBookingFableModel";

export function RepeatBadge({ rule = STORED_RULE }: { rule?: RecurrenceRule }) {
  return (
    <Badge variant="outline">
      <Repeat2Icon aria-hidden="true" />
      {describeRule(rule)}
    </Badge>
  );
}

export function SeriesFacts({ booking, action }: { booking: Booking; action?: React.ReactNode }) {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const format = useOccurrenceFormat();
  const headingId = React.useId();
  const series = seriesOf(booking);
  const minutes = Math.max(0, Math.round((Date.parse(booking.end) - Date.parse(booking.start)) / 60000));
  const facts: Array<[string, React.ReactNode]> = [
    [
      t("bookings.details.when"),
      <span key="when">
        <BookingInstrumentTimeTooltip
          start={booking.start}
          end={booking.end}
          displayTimeZone={DISPLAY_TIMEZONE}
          instrumentTimeZone={booking.timezone}
        >
          <span>
            <time dateTime={booking.start}>
              {formatBookingDateTime(booking.start, DISPLAY_TIMEZONE, i18n.language, timeFormat)}
            </time>
            {" – "}
            <time dateTime={booking.end}>
              {formatBookingDateTime(booking.end, DISPLAY_TIMEZONE, i18n.language, timeFormat)}
            </time>
          </span>
        </BookingInstrumentTimeTooltip>
        <span className="text-muted-foreground">{` · ${t("bookableItemDetails.minutes", { count: minutes })}`}</span>
      </span>,
    ],
    ...(series
      ? ([
          [
            "Repeats",
            <span key="repeats" className="flex flex-wrap items-center gap-2">
              <RepeatBadge />
              <span>
                {series.position} of {series.count} · until {format.date(lastBooking.start)}
              </span>
            </span>,
          ],
        ] as Array<[string, React.ReactNode]>)
      : []),
    ...(booking.privacy === "full" && booking.bookedBy
      ? ([[t("bookings.details.bookedBy"), <UserBadge key="booked-by" name={booking.bookedBy} />]] as Array<
          [string, React.ReactNode]
        >)
      : []),
    [
      t("bookings.form.purpose"),
      booking.purpose ? (
        <span key="purpose" className="whitespace-pre-line">
          {booking.purpose}
        </span>
      ) : (
        <span key="purpose" className="text-muted-foreground">
          {t("bookings.details.noneProvided")}
        </span>
      ),
    ],
  ];
  return (
    <Panel heading={t("bookings.details.title")} headingId={headingId} action={action}>
      <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
        <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
          {facts.map(([label, value]) => (
            <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
              <dt className="font-medium">{label}</dt>
              <dd className="min-w-0">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Scope choice for cancel and edit (THIS | FOLLOWING | SERIES), following DeleteBookingDialog's structure.
// ---------------------------------------------------------------------------

function ScopeOptions({
  action,
  series,
  scope,
  ruleChanged = false,
  onChange,
}: {
  action: "cancel" | "edit";
  series: SeriesMembership;
  scope: Scope;
  ruleChanged?: boolean;
  onChange: (scope: Scope) => void;
}) {
  const id = React.useId();
  const format = useOccurrenceFormat();
  const remaining = affectedCount("FOLLOWING", series);
  const regenerated =
    action === "edit" ? " Bookings are re-created, and any occurrence moved earlier is replaced." : "";
  const options: ReadonlyArray<{ value: Scope; label: string; detail: string; disabled?: boolean }> = [
    {
      value: "THIS",
      label: "This occurrence",
      detail: ruleChanged
        ? "Not available while the repeat rule is changed."
        : `Only ${format.date(currentBooking.start)} (${series.position} of ${series.count}).`,
      disabled: ruleChanged,
    },
    {
      value: "FOLLOWING",
      label: "This and following occurrences",
      detail: `${remaining} bookings from ${format.date(currentBooking.start)} to ${format.date(lastBooking.start)}.${regenerated}`,
    },
    {
      value: "SERIES",
      label: "Entire series",
      detail: `${remaining} remaining bookings. Past occurrences stay in history.${regenerated}`,
    },
  ];
  return (
    <RadioGroup
      aria-label="Apply to"
      value={scope}
      onValueChange={(value) => onChange(String(value) as Scope)}
      className="flex flex-col gap-3"
    >
      {options.map((option) => (
        <div key={option.value} className="flex items-start gap-2">
          <RadioGroupItem
            value={option.value}
            id={`${id}-${option.value}`}
            disabled={option.disabled}
            className="mt-0.5"
          />
          <div className="grid gap-0.5">
            <Label htmlFor={`${id}-${option.value}`}>{option.label}</Label>
            <p className="text-xs text-muted-foreground">{option.detail}</p>
          </div>
        </div>
      ))}
    </RadioGroup>
  );
}

function CancelSeriesDialog({ booking, series }: { booking: Booking; series: SeriesMembership }) {
  const { t } = useTranslation("booking");
  const format = useOccurrenceFormat();
  const [open, setOpen] = React.useState(false);
  const [scope, setScope] = React.useState<Scope>("THIS");
  const [reason, setReason] = React.useState("");
  const [outcome, setOutcome] = React.useState<{ scope: Scope; count: number; reason: string }>();
  const reasonId = React.useId();
  const reasonHintId = `${reasonId}-hint`;
  const reasonCountId = `${reasonId}-count`;
  const count = affectedCount(scope, series);
  const period = `${format.date(booking.start)}, ${format.period(booking.start, booking.end)}`;
  const openChange = (next: boolean) => {
    if (next) {
      setScope("THIS");
      setReason("");
    }
    setOpen(next);
  };
  return (
    <div className="space-y-3">
      <AlertDialog open={open} onOpenChange={openChange}>
        <AlertDialogTrigger render={<Button type="button" size="sm" variant="destructive" />}>
          {t("bookings.actions.cancel")}
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookings.cancelDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookings.cancelDialog.description", { itemName: item.name, period })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <ScopeOptions action="cancel" series={series} scope={scope} onChange={setScope} />
          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor={reasonId}>
              {t("bookings.cancelDialog.reasonLabel")}
            </label>
            <Textarea
              id={reasonId}
              value={reason}
              maxLength={CANCELLATION_REASON_MAX_LENGTH}
              rows={3}
              aria-describedby={`${reasonHintId} ${reasonCountId}`}
              onChange={(event) => setReason(event.currentTarget.value)}
            />
            <div className="flex items-start justify-between gap-3 text-xs text-muted-foreground">
              <p id={reasonHintId}>{t("bookings.cancelDialog.reasonHint")}</p>
              <span id={reasonCountId} className="shrink-0" aria-live="polite">
                {t("bookings.cancelDialog.reasonCount", { count: reason.length })}
              </span>
            </div>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("bookings.cancelDialog.keep")}</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              variant="destructive"
              onClick={() => {
                setOutcome({ scope, count, reason: reason.trim() });
                setOpen(false);
              }}
            >
              {count === 1 ? t("bookings.actions.cancel") : `Cancel ${count} bookings`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <p role="status" aria-live="polite" className="text-sm">
        {outcome ? (
          <>
            {outcome.count === 1 ? t("bookings.details.bookingCancelled") : `${outcome.count} bookings cancelled.`}
            {outcome.reason ? ` Reason: ${outcome.reason}` : ""}{" "}
            {outcome.scope === "THIS" ? (
              <Button type="button" size="xs" variant="link" onClick={() => setOutcome(undefined)}>
                Undo
              </Button>
            ) : null}
          </>
        ) : null}
      </p>
    </div>
  );
}

function EditSeriesPrototype({ booking, series }: { booking: Booking; series: SeriesMembership }) {
  const { t } = useTranslation("booking");
  const formId = React.useId();
  const headingId = React.useId();
  const saveRef = React.useRef<HTMLButtonElement>(null);
  const [formKey, setFormKey] = React.useState(0);
  const [rule, setRule] = React.useState(STORED_RULE);
  const [formState, setFormState] = React.useState<BookingFormState>();
  const [pending, setPending] = React.useState<BookingFormSubmission>();
  const [scope, setScope] = React.useState<Scope>("FOLLOWING");
  const [outcome, setOutcome] = React.useState<string>();
  const ruleChanged = !sameRule(rule, STORED_RULE);
  const draft = formState?.draft ?? INITIAL_WINDOW;
  const changed = ruleChanged || formState?.dirty === true;
  // A changed window or rule previews the FOLLOWING result: the stored rule continues for the remaining count.
  const previewRule = React.useMemo<RecurrenceRule>(() => {
    if (ruleChanged || rule.ends.kind !== "count") return rule;
    return { ...rule, ends: { kind: "count", count: affectedCount("FOLLOWING", series) } };
  }, [rule, ruleChanged, series]);
  const problem = ruleProblem(previewRule, draft);
  const occurrences = React.useMemo(
    () => (changed && formState?.window ? dryRun(previewRule, formState.draft) : []),
    [changed, formState, previewRule],
  );
  const conflicts = occurrences.filter((occurrence) => occurrence.status !== "ok");
  // The form opens scope selection; validate only that scope before confirming the edit.
  const singleConflicts = formState?.window
    ? dryRun({ ...previewRule, frequency: "NONE" }, formState.draft).filter((occurrence) => occurrence.status !== "ok")
    : [];
  const scopedConflicts = scope === "THIS" ? singleConflicts : conflicts;
  const blocked = problem !== undefined;
  const reset = () => {
    setFormKey((key) => key + 1);
    setRule(STORED_RULE);
    setFormState(undefined);
    setOutcome(undefined);
  };
  return (
    <div className="space-y-6">
      <Panel
        heading={t("bookings.details.edit.title")}
        headingId={headingId}
        action={
          <span className="flex gap-3">
            <Button ref={saveRef} type="submit" size="xs" form={formId} disabled={blocked}>
              <CheckIcon aria-hidden="true" />
              {t("bookings.form.save")}
            </Button>
            <Button type="button" size="xs" variant="ghost" onClick={reset}>
              <XIcon aria-hidden="true" />
              {t("bookings.details.edit.discard")}
            </Button>
          </span>
        }
      >
        <BookingForm
          key={formKey}
          mode="edit"
          layout="inline"
          formId={formId}
          displayTimezone={DISPLAY_TIMEZONE}
          token={OAUTH_TOKEN}
          pending={false}
          booking={editableBooking(booking)}
          configuration={item}
          error={problem}
          submissionBlocked={blocked}
          onStateChange={setFormState}
          afterWindowFields={<RepeatPanel rule={rule} draft={draft} problem={problem} onChange={setRule} />}
          onSubmit={async (submission) => {
            setPending(submission);
            setScope(ruleChanged ? "FOLLOWING" : "THIS");
          }}
        />
      </Panel>
      {changed ? (
        <OccurrencePreview rule={previewRule} draft={draft} occurrences={occurrences} problem={problem} />
      ) : null}
      <AlertDialog open={pending !== undefined} onOpenChange={(open) => (open ? undefined : setPending(undefined))}>
        <AlertDialogContent finalFocus={saveRef}>
          <AlertDialogHeader>
            <AlertDialogTitle>Apply changes to which bookings?</AlertDialogTitle>
            <AlertDialogDescription>
              This booking is part of a series: {describeRule(STORED_RULE)}, {series.count} occurrences.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <ScopeOptions action="edit" series={series} scope={scope} ruleChanged={ruleChanged} onChange={setScope} />
          {scopedConflicts.length > 0 ? (
            <p role="alert" className="text-sm text-destructive">
              {scopedConflicts.length} occurrences cannot be booked. Choose another scope or change the booking.
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel>Back</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              disabled={blocked || scopedConflicts.length > 0}
              onClick={() => {
                const count = affectedCount(scope, series);
                setOutcome(`Updated ${count} ${count === 1 ? "booking" : "bookings"}.`);
                setPending(undefined);
              }}
            >
              {`Update ${affectedCount(scope, series)} ${affectedCount(scope, series) === 1 ? "booking" : "bookings"}`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      {outcome ? (
        <p role="status" className="text-sm">
          {outcome}
        </p>
      ) : null}
    </div>
  );
}

export function ScopePrototype() {
  const series = seriesOf(currentBooking);
  if (!series) throw new Error("Prototype fixture must be a series booking");
  return (
    <main className="space-y-8 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {item.name}
      </Heading>
      <div className="max-w-3xl space-y-8">
        <EditSeriesPrototype booking={currentBooking} series={series} />
        <SeriesFacts booking={currentBooking} />
        <CancelSeriesDialog booking={currentBooking} series={series} />
      </div>
    </main>
  );
}
