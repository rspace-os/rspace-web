// PROTOTYPE ONLY. Three Storybook concepts for the proposed recurring-booking flow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy is intentionally not entering the translation catalog. */

import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { useState } from "react";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { InlineConcept, type Inspection, ReviewConcept } from "./RecurringBookingCodexEntryConcepts.story";
import { prototypeWorker } from "./RecurringBookingCodexPreview.story";
import { WorkspaceConcept } from "./RecurringBookingCodexWorkspace.story";
import { type CodexScenario, codexScenarios } from "./recurringBookingCodexFixtures";

type Concept = "inline" | "review" | "workspace";

const scenarioOptions: ReadonlyArray<{ key: CodexScenario; label: string }> = [
  { key: "ready", label: "Available" },
  { key: "daily", label: "Daily" },
  { key: "conflict", label: "Conflict" },
  { key: "dst", label: "DST shift" },
  { key: "maintenance", label: "Maintenance" },
  { key: "uncertain", label: "Uncertain save" },
  { key: "previewError", label: "Preview error" },
];

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
