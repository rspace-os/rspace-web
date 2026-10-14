// PROTOTYPE ONLY (Opus 5.5). Production BookingForm with a vertical day-schedule aside; all data stays in memory.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CheckIcon, ChevronDownIcon, XIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingForm, type BookingFormState, type BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import { Panel } from "@/modules/booking/pages/bookings/BookingEventContext";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Alert, AlertDescription } from "@/modules/common/ui/alert";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";
import {
  type AvailabilityFixture,
  type DayQueryFixture,
  DraftReadout,
  draftConflicts,
  draftStatus,
  editableBookingFor,
  FixtureSelect,
  formatInterval,
  HOUR_HEIGHT_OPTIONS,
  originalFixtureFor,
  PrototypeControls,
  QUERY_OPTIONS,
  SCENARIO_OPTIONS,
  SCENARIOS,
  type Scenario,
  type ScenarioId,
  sameDraft,
  useSimulatedDayQuery,
  VerticalDayTimeline,
} from "./verticalDayTimelineOpusPrototype.story";

type Mode = "add" | "edit";
type Args = {
  mode: Mode;
  scenarioId: ScenarioId;
  query: DayQueryFixture;
  availability: AvailabilityFixture;
  hourHeight: number;
  /** Simulated device width in px; undefined uses the Storybook canvas width. */
  frameWidth?: number;
};
type SimulatedRequest = { start: string; end: string; purpose: string | null; count: number };

const AVAILABILITY_OPTIONS: readonly { value: AvailabilityFixture; label: string }[] = [
  { value: "ok", label: "Checked" },
  { value: "pending", label: "Checking" },
  { value: "failed", label: "Check failed" },
];
const WIDE_CONTAINER_PX = 672; // @2xl, matching AddBookingContent's grid breakpoint.

function BookingPage({
  mode,
  scenario,
  allowDoubleBooking,
  query,
  onRetry,
  availability,
  hourHeight,
}: {
  mode: Mode;
  scenario: Scenario;
  allowDoubleBooking: boolean;
  query: DayQueryFixture;
  onRetry: () => void;
  availability: AvailabilityFixture;
  hourHeight: number;
}) {
  const { t } = useTranslation("booking");
  const timezone = scenario.displayTimezone;
  const item = React.useMemo(() => ({ ...scenario.item, allowDoubleBooking }), [scenario.item, allowDoubleBooking]);
  const editBooking = React.useMemo(
    () => (mode === "edit" ? editableBookingFor(scenario) : undefined),
    [mode, scenario],
  );
  const events = React.useMemo(
    () => (mode === "edit" ? [...scenario.events, originalFixtureFor(scenario)] : scenario.events),
    [mode, scenario],
  );
  const formId = React.useId();
  const panelHeadingId = React.useId();
  const [formKey, setFormKey] = React.useState(0);
  const [formState, setFormState] = React.useState<BookingFormState>();
  // One pending timeline adjustment; only a matching form snapshot acknowledges it.
  const [adjustment, setAdjustment] = React.useState<typeof scenario.initialWindow>();
  const [submitting, setSubmitting] = React.useState(false);
  const [request, setRequest] = React.useState<SimulatedRequest>();
  const [scheduleOpen, setScheduleOpen] = React.useState(false);
  const [container, setContainer] = React.useState<HTMLDivElement | null>(null);
  const [wide, setWide] = React.useState(true);

  React.useLayoutEffect(() => {
    if (!container) return;
    const update = () => setWide(container.clientWidth >= WIDE_CONTAINER_PX);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(container);
    return () => observer.disconnect();
  }, [container]);

  const onStateChange = React.useCallback((state: BookingFormState) => {
    setFormState(state);
    setAdjustment((pending) => (pending && sameDraft(pending, state.draft) ? undefined : pending));
  }, []);

  const committedDraft = adjustment ?? formState?.draft ?? scenario.initialWindow;
  const unchangedEdit =
    mode === "edit" && formState !== undefined && sameDraft(formState.draft, scenario.initialWindow);
  // Availability covers the complete committed draft, never a preview or an unacknowledged adjustment.
  const checkedWindow = adjustment ? undefined : formState?.window;
  const check = draftConflicts(checkedWindow, events, item, timezone, editBooking?.id);
  const checking = availability === "pending" && checkedWindow !== undefined && !unchangedEdit;
  const blocking = availability === "ok" && check.blocking && !unchangedEdit;
  const conflicts = availability === "ok" ? check.conflicts : [];
  const submissionBlocked = adjustment !== undefined || checking || blocking;
  const status = draftStatus({
    resolved: Boolean(checkedWindow ?? adjustment),
    availability,
    conflictCount: conflicts.length,
    blocking: check.blocking && !unchangedEdit,
  });

  const submit = async (submission: BookingFormSubmission) => {
    setSubmitting(true);
    try {
      await new Promise((resolve) => setTimeout(resolve, 600));
      setRequest((previous) => ({
        start: submission.window.start,
        end: submission.window.end,
        purpose: submission.purpose,
        count: (previous?.count ?? 0) + 1,
      }));
    } finally {
      setSubmitting(false);
    }
  };
  const reset = () => {
    setFormKey((key) => key + 1);
    setFormState(undefined);
    setAdjustment(undefined);
    setRequest(undefined);
  };

  const commonFormProps = {
    displayTimezone: timezone,
    token: "prototype",
    pending: false,
    conflicts,
    conflictSeverity: blocking ? ("error" as const) : ("warning" as const),
    submissionBlocked,
    windowAdjustment: adjustment,
    onStateChange,
    onSubmit: submit,
  };
  const form =
    mode === "add" ? (
      <BookingForm
        key={formKey}
        {...commonFormProps}
        mode="add"
        eventKind="BOOKING"
        initialTarget={item}
        initialDate={scenario.date}
        initialWindow={scenario.initialWindow}
        // ponytail: locked so the item picker never queries the API; production keeps the picker.
        lockTarget
        showMobileItemInformation
        showRulesSummary={false}
        onCancel={reset}
      />
    ) : editBooking ? (
      <Panel
        heading={t("bookings.details.edit.title")}
        headingId={panelHeadingId}
        action={
          <span className="flex gap-3">
            <Button
              type="submit"
              size="xs"
              form={formId}
              disabled={submitting || submissionBlocked}
              aria-busy={submitting}
            >
              <CheckIcon aria-hidden="true" />
              {t("bookings.form.save")}
            </Button>
            <Button type="button" size="xs" variant="ghost" disabled={submitting} onClick={reset}>
              <XIcon aria-hidden="true" />
              {t("bookings.details.edit.discard")}
            </Button>
          </span>
        }
      >
        <BookingForm
          key={formKey}
          {...commonFormProps}
          mode="edit"
          layout="inline"
          formId={formId}
          booking={editBooking}
          configuration={item}
        />
      </Panel>
    ) : null;

  const timeline = (
    <VerticalDayTimeline
      item={item}
      displayTimezone={timezone}
      events={events}
      fallbackDate={scenario.date}
      originalBookingId={editBooking?.id}
      draft={committedDraft}
      awaitingAcknowledgement={adjustment !== undefined}
      disabled={submitting}
      query={query}
      onRetry={onRetry}
      status={status}
      hourHeight={hourHeight}
      layout={wide ? "sticky" : "inline"}
      onCommit={setAdjustment}
    />
  );

  return (
    <>
      <PrototypeControls>
        <dl className="grid w-full grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1">
          <dt className="font-medium">Form draft</dt>
          <dd>{formState ? <DraftReadout draft={formState.draft} /> : "—"}</dd>
          <dt className="font-medium">Pending adjustment</dt>
          <dd>{adjustment ? <DraftReadout draft={adjustment} /> : "None"}</dd>
          <dt className="font-medium">Purpose / dirty</dt>
          <dd>{formState ? `"${formState.purpose}" · ${formState.dirty ? "dirty" : "clean"}` : "—"}</dd>
          <dt className="font-medium">Simulated request</dt>
          <dd className="min-w-0 break-words">
            <span role="status">
              {request
                ? `#${request.count} ${formatInterval(request.start, request.end, timezone, "en-GB")} · ${request.start} → ${request.end} · purpose ${request.purpose === null ? "null" : `"${request.purpose}"`}`
                : "Not submitted"}
            </span>
          </dd>
        </dl>
      </PrototypeControls>
      {mode === "add" ? (
        <Heading level={3} as="h1">
          {t("bookings.addTitle")}
        </Heading>
      ) : (
        <Heading level={3} as="h1">
          {item.name}
        </Heading>
      )}
      {availability === "failed" && checkedWindow ? (
        <Alert className="border-amber-600 bg-amber-100 text-amber-950 dark:bg-amber-950 dark:text-amber-200">
          <AlertDescription className="text-amber-950 dark:text-amber-200">
            Availability couldn't be checked. You can still save; the server will confirm.
          </AlertDescription>
        </Alert>
      ) : null}
      <div ref={setContainer} className="@container">
        <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_24rem]">
          <div className="min-w-0 space-y-6">
            {!wide && mode === "edit" ? (
              <BookingItemInformationCard item={item} displayTimezone={timezone} as="section" />
            ) : null}
            {form}
            {wide ? null : (
              <Collapsible open={scheduleOpen} onOpenChange={setScheduleOpen} className="space-y-2">
                <CollapsibleTrigger
                  className={buttonVariants({ variant: "outline", className: "w-full justify-between" })}
                >
                  <span>Day schedule</span>
                  <span className="min-w-0 truncate text-xs text-muted-foreground">{status.text}</span>
                  <ChevronDownIcon
                    aria-hidden="true"
                    className={cn("transition-transform", scheduleOpen && "rotate-180")}
                  />
                </CollapsibleTrigger>
                <CollapsibleContent>{timeline}</CollapsibleContent>
              </Collapsible>
            )}
          </div>
          {wide ? (
            <div className="min-w-0 space-y-4">
              {/* Wrapped so only the schedule, not the item card, is sticky. */}
              <div>
                <BookingItemInformationCard item={item} displayTimezone={timezone} />
              </div>
              {timeline}
            </div>
          ) : null}
        </div>
      </div>
    </>
  );
}

function BookingFormCalendarAsideOpusPrototype(args: Args) {
  const [scenarioId, setScenarioId] = React.useState(args.scenarioId);
  const [query, setQuery, retry] = useSimulatedDayQuery(args.query);
  const [availability, setAvailability] = React.useState(args.availability);
  const [allowDoubleBooking, setAllowDoubleBooking] = React.useState<boolean>(
    SCENARIOS[args.scenarioId].item.allowDoubleBooking,
  );
  const [hourHeight, setHourHeight] = React.useState(args.hourHeight);
  const scenario = SCENARIOS[scenarioId];
  const doubleBookingId = React.useId();
  return (
    <main className="min-h-screen bg-background text-foreground">
      <div className="space-y-4 p-4 sm:p-8">
        <PrototypeControls>
          <FixtureSelect
            label="Scenario"
            value={scenarioId}
            options={SCENARIO_OPTIONS}
            onChange={(next) => {
              setScenarioId(next);
              setAllowDoubleBooking(SCENARIOS[next].item.allowDoubleBooking);
            }}
          />
          <FixtureSelect label="Day query" value={query} options={QUERY_OPTIONS} onChange={setQuery} />
          <FixtureSelect
            label="Availability check"
            value={availability}
            options={AVAILABILITY_OPTIONS}
            onChange={setAvailability}
          />
          <FixtureSelect
            label="Hour height"
            value={hourHeight}
            options={HOUR_HEIGHT_OPTIONS}
            onChange={setHourHeight}
          />
          <div className="flex h-8 items-center gap-2">
            <input
              id={doubleBookingId}
              type="checkbox"
              checked={allowDoubleBooking}
              onChange={(event) => setAllowDoubleBooking(event.currentTarget.checked)}
            />
            <label htmlFor={doubleBookingId} className="font-medium">
              Double booking allowed
            </label>
          </div>
        </PrototypeControls>
      </div>
      <div
        className={cn(
          "space-y-6 p-4 sm:p-8",
          args.frameWidth && "mx-auto mb-8 rounded-sm border-2 border-dashed border-muted-foreground p-3 sm:p-3",
        )}
        style={args.frameWidth ? { maxWidth: args.frameWidth } : undefined}
      >
        <BookingPage
          key={`${scenarioId}|${args.mode}`}
          mode={args.mode}
          scenario={scenario}
          allowDoubleBooking={allowDoubleBooking}
          query={query}
          onRetry={retry}
          availability={availability}
          hourHeight={hourHeight}
        />
      </div>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking form calendar aside (Opus 5.5)",
  component: BookingFormCalendarAsideOpusPrototype,
  parameters: { layout: "fullscreen" },
  args: { mode: "add", scenarioId: "standard", query: "ready", availability: "ok", hourHeight: 72 },
  argTypes: {
    mode: { control: "inline-radio", options: ["add", "edit"] },
    scenarioId: { control: "select", options: Object.keys(SCENARIOS) },
    query: { control: "select", options: QUERY_OPTIONS.map((option) => option.value) },
    availability: { control: "select", options: AVAILABILITY_OPTIONS.map((option) => option.value) },
    hourHeight: { control: "select", options: HOUR_HEIGHT_OPTIONS.map((option) => option.value) },
    frameWidth: { control: "number" },
  },
  render: (args) => <BookingFormCalendarAsideOpusPrototype key={JSON.stringify(args)} {...args} />,
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof BookingFormCalendarAsideOpusPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AddBooking: Story = {};
export const EditBooking: Story = { args: { mode: "edit" } };
export const AddDenseWithAllowedOverlap: Story = { args: { scenarioId: "dense", hourHeight: 48 } };
export const AddWithFailedDayQuery: Story = { args: { query: "error", availability: "failed" } };
export const NarrowViewport: Story = { args: { frameWidth: 360 } };
