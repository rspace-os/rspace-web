// PROTOTYPE ONLY (Opus 5.5). Standalone vertical day timeline with a controlled in-memory draft.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Button } from "@/modules/common/ui/button";
import {
  type DayQueryFixture,
  DraftReadout,
  draftConflicts,
  draftStatus,
  FixtureSelect,
  HOUR_HEIGHT_OPTIONS,
  PrototypeControls,
  QUERY_OPTIONS,
  SCENARIO_OPTIONS,
  SCENARIOS,
  type Scenario,
  type ScenarioId,
  useSimulatedDayQuery,
  VerticalDayTimeline,
} from "./verticalDayTimelineOpusPrototype.story";

type Args = { scenarioId: ScenarioId; query: DayQueryFixture; hourHeight: number };

function Body({
  scenario,
  query,
  onRetry,
  hourHeight,
}: {
  scenario: Scenario;
  query: DayQueryFixture;
  onRetry: () => void;
  hourHeight: number;
}) {
  const [draft, setDraft] = React.useState(scenario.initialWindow);
  const [commits, setCommits] = React.useState(0);
  const window = resolveBookingWindow(draft, scenario.displayTimezone).window;
  const check = draftConflicts(window, scenario.events, scenario.item, scenario.displayTimezone);
  return (
    <>
      <VerticalDayTimeline
        item={scenario.item}
        displayTimezone={scenario.displayTimezone}
        events={scenario.events}
        fallbackDate={scenario.date}
        draft={draft}
        query={query}
        onRetry={onRetry}
        status={draftStatus({
          resolved: Boolean(window),
          availability: "ok",
          conflictCount: check.conflicts.length,
          blocking: check.blocking,
        })}
        hourHeight={hourHeight}
        layout="inline"
        onCommit={(next) => {
          setDraft(next);
          setCommits((count) => count + 1);
        }}
      />
      <PrototypeControls>
        <p className="w-full">
          Committed form fields: <DraftReadout draft={draft} />
        </p>
        <p>Commits: {commits}</p>
        <Button
          type="button"
          size="xs"
          variant="outline"
          onClick={() => {
            setDraft(scenario.initialWindow);
            setCommits(0);
          }}
        >
          Reset draft
        </Button>
      </PrototypeControls>
    </>
  );
}

function VerticalDayTimelineOpusPrototype(args: Args) {
  const [scenarioId, setScenarioId] = React.useState(args.scenarioId);
  const [query, setQuery, retry] = useSimulatedDayQuery(args.query);
  const [hourHeight, setHourHeight] = React.useState(args.hourHeight);
  const scenario = SCENARIOS[scenarioId];
  return (
    <main className="min-h-screen bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-md space-y-4">
        <PrototypeControls>
          <FixtureSelect label="Scenario" value={scenarioId} options={SCENARIO_OPTIONS} onChange={setScenarioId} />
          <FixtureSelect label="Day query" value={query} options={QUERY_OPTIONS} onChange={setQuery} />
          <FixtureSelect
            label="Hour height"
            value={hourHeight}
            options={HOUR_HEIGHT_OPTIONS}
            onChange={setHourHeight}
          />
        </PrototypeControls>
        <Body key={scenarioId} scenario={scenario} query={query} onRetry={retry} hourHeight={hourHeight} />
      </div>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Vertical day timeline (Opus 5.5)",
  component: VerticalDayTimelineOpusPrototype,
  parameters: { layout: "fullscreen" },
  args: { scenarioId: "standard", query: "ready", hourHeight: 72 },
  argTypes: {
    scenarioId: { control: "select", options: Object.keys(SCENARIOS) },
    query: { control: "select", options: QUERY_OPTIONS.map((option) => option.value) },
    hourHeight: { control: "select", options: HOUR_HEIGHT_OPTIONS.map((option) => option.value) },
  },
  // Remount when Storybook args change so the in-page fixture state starts from them.
  render: (args) => <VerticalDayTimelineOpusPrototype key={JSON.stringify(args)} {...args} />,
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof VerticalDayTimelineOpusPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StandaloneDay: Story = {};
export const DenseAndShortEvents: Story = { args: { scenarioId: "dense", hourHeight: 48 } };
export const SpringForwardDst: Story = { args: { scenarioId: "springForward" } };
export const FallBackRepeatedHour: Story = { args: { scenarioId: "fallBack", hourHeight: 120 } };
export const OvernightCrossDay: Story = { args: { scenarioId: "overnight", hourHeight: 48 } };
export const DisplayTimezoneDiffers: Story = { args: { scenarioId: "displayDiffers" } };
export const FailedDayQuery: Story = { args: { query: "error" } };
