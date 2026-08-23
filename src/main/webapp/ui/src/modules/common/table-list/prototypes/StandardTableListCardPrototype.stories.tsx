/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { BookOpenIcon, ExternalLinkIcon, ListFilterIcon, SearchIcon } from "lucide-react";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Input } from "@/modules/common/ui/input";
import { StandardTableListCardGrid, type StandardTableListCardItem } from "./StandardTableListCardPrototype";

const records: readonly StandardTableListCardItem[] = [
  {
    id: "SD1001",
    accessibleName: "Experiment notes",
    title: (
      <a className="font-heading font-medium text-link hover:underline" href="#SD1001">
        Experiment notes
      </a>
    ),
    selection: <Checkbox aria-label="Select Experiment notes" />,
    fields: [
      { id: "owner", label: "Owner", value: "Ada Lovelace" },
      { id: "modified", label: "Modified", value: "17 August 2026" },
      { id: "type", label: "Type", value: "Basic document" },
    ],
    actions: (
      <Button type="button" size="sm" variant="outline">
        <ExternalLinkIcon aria-hidden="true" /> Open
      </Button>
    ),
  },
  {
    id: "FL1002",
    accessibleName: "Microscopy results with a deliberately long record title",
    title: (
      <a className="font-heading font-medium text-link hover:underline" href="#FL1002">
        Microscopy results with a deliberately long record title
      </a>
    ),
    selection: <Checkbox aria-label="Select Microscopy results" />,
    fields: [
      { id: "owner", label: "Owner", value: "Grace Hopper" },
      { id: "modified", label: "Modified", value: "16 August 2026" },
      { id: "type", label: "Type", value: "Folder" },
    ],
    actions: (
      <Button type="button" size="sm" variant="outline">
        <ExternalLinkIcon aria-hidden="true" /> Open
      </Button>
    ),
  },
];

const textHeavyRecords: readonly StandardTableListCardItem[] = [
  {
    id: "TH1001",
    accessibleName: "A unified account of reproducibility across distributed microscopy facilities",
    title: (
      <a className="font-heading text-base font-medium leading-snug text-link hover:underline" href="#TH1001">
        A unified account of reproducibility across distributed microscopy facilities
      </a>
    ),
    selection: <Checkbox aria-label="Select unified account of reproducibility" />,
    fields: [
      {
        id: "abstract",
        label: "Abstract",
        value: (
          <div className="space-y-2 leading-6">
            <p>
              This study compares image-acquisition protocols used by twelve facilities over a five-year period. It
              identifies where calibration drift, operator training, and undocumented preprocessing create the largest
              differences between otherwise equivalent experiments.
            </p>
            <p>
              The resulting framework separates procedural variation from instrument variation and proposes a minimal
              set of metadata needed to reproduce an acquisition elsewhere.
            </p>
          </div>
        ),
      },
      { id: "authors", label: "Authors", value: "Amara Okafor, Lin Wei, Sofia Rossi, and 14 collaborators" },
      {
        id: "keywords",
        label: "Keywords",
        value: (
          <div className="flex flex-wrap gap-1.5">
            <Badge variant="secondary">reproducibility</Badge>
            <Badge variant="secondary">microscopy</Badge>
            <Badge variant="secondary">metadata standards</Badge>
          </div>
        ),
      },
      { id: "updated", label: "Updated", value: "17 August 2026 at 16:42 by Amara Okafor" },
    ],
    actions: (
      <>
        <Button type="button" size="sm" variant="outline">
          Cite
        </Button>
        <Button type="button" size="sm">
          <BookOpenIcon aria-hidden="true" /> Read
        </Button>
      </>
    ),
  },
  {
    id: "TH1002",
    accessibleName: "Field observations from the Northern Wetlands restoration programme",
    title: (
      <a className="font-heading text-base font-medium leading-snug text-link hover:underline" href="#TH1002">
        Field observations from the Northern Wetlands restoration programme, seasons one through eight
      </a>
    ),
    selection: <Checkbox aria-label="Select Northern Wetlands observations" />,
    fields: [
      {
        id: "description",
        label: "Description",
        value:
          "A curated narrative record of water levels, vegetation recovery, soil chemistry, weather anomalies, and species sightings collected by rotating field teams. Notes retain the original terminology used by each team and include editorial annotations where classifications changed during the programme.",
      },
      {
        id: "coverage",
        label: "Coverage",
        value: "March 2018–November 2025 · 47 sites · 18,240 observations · 6 regional partners",
      },
      { id: "rights", label: "Access", value: "Embargoed until 1 March 2027; metadata is publicly visible" },
      { id: "notes", label: "Curator note", value: <span className="italic text-muted-foreground">Not supplied</span> },
    ],
    actions: (
      <Button type="button" size="sm" variant="outline">
        View metadata
      </Button>
    ),
  },
];

function GenericRecordsExample() {
  return (
    <main className="min-h-screen bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-5xl space-y-4">
        <h1 className="font-heading text-2xl font-semibold">Research records</h1>
        <StandardTableListCardGrid label="Research record cards" items={records} />
      </div>
    </main>
  );
}

function TextHeavyCollectionExample() {
  return (
    <main className="min-h-screen bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-6xl space-y-5">
        <header className="space-y-1">
          <h1 className="font-heading text-2xl font-semibold">Knowledge library</h1>
          <p className="text-sm text-muted-foreground">
            A theoretical collection designed to stress-test long-form content in the standard card view.
          </p>
        </header>
        <div className="flex max-w-xl gap-2">
          <div className="relative min-w-0 flex-1">
            <SearchIcon
              aria-hidden="true"
              className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            />
            <Input className="pl-9" aria-label="Search knowledge library" placeholder="Search titles and full text" />
          </div>
          <Button type="button" variant="outline">
            <ListFilterIcon aria-hidden="true" /> Filters
          </Button>
        </div>
        <StandardTableListCardGrid label="Knowledge library cards" items={textHeavyRecords} />
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>1–2 of 2</span>
          <span>Page 1 of 1</span>
        </div>
        <output className="block rounded-sm border border-dashed bg-muted/30 px-3 py-2 font-mono text-xs text-muted-foreground">
          Prototype state: collection=text-heavy; view=card; layout=standard; visibleFields=4; selected=0
        </output>
      </div>
    </main>
  );
}

const meta = {
  title: "Components/Table List/Prototypes/StandardCard",
  component: GenericRecordsExample,
  parameters: { layout: "fullscreen", viewport: { defaultViewport: "mobile1" } },
} satisfies Meta<typeof GenericRecordsExample>;

export default meta;
type Story = StoryObj<typeof meta>;
export const ResearchRecords: Story = {};
export const TextHeavyCollection: Story = { render: () => <TextHeavyCollectionExample /> };
