// PROTOTYPE ONLY. Three Storybook concepts for the proposed recurring-booking flow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy is intentionally not entering the translation catalog. */

import { AlertTriangleIcon, ArrowLeftIcon, CheckIcon, InfoIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { BookingDayTimelineAside } from "@/modules/booking/creation/BookingDayTimelineAside";
import type { BookingFormState, BookingFormSubmission } from "@/modules/booking/creation/BookingForm";
import { BookingForm } from "@/modules/booking/creation/BookingForm";
import { BookingItemInformationCard } from "@/modules/booking/creation/BookingItemInformation";
import { useBookingTimelineDraft } from "@/modules/booking/creation/useBookingTimelineDraft";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Heading } from "@/modules/common/ui/typography";
import {
  emptyRows,
  formatDate,
  PreviewPanel,
  prototypeDisplayTimezone,
  RepeatControls,
  useFixturePreview,
} from "./RecurringBookingCodexPreview.story";
import { type CodexRule, type CodexScenario, codexScenarios } from "./recurringBookingCodexFixtures";

export type Inspection = Record<string, unknown>;

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

export function InlineConcept({
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

export function ReviewConcept({
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
