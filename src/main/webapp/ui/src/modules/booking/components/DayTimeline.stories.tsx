import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { expect, fireEvent, userEvent, waitFor, within } from "storybook/test";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { DayTimeline, type DayTimelineEvent, type DayTimelineViewState } from "./DayTimeline";
import { DayTimelineStory } from "./DayTimeline.story";

const denseBookings: Array<DayTimelineEvent> = Array.from({ length: 12 }, (_, index) => ({
  id: `dense-${index}`,
  kind: "booking",
  privacy: "full",
  title: `Confocal microscope · researcher.${String(index + 1).padStart(2, "0")}`,
  bookedBy: `researcher.${String(index + 1).padStart(2, "0")}`,
  item: { name: "Confocal microscope", globalId: "IN123" },
  canEdit: false,
  notes: `Sample check ${index + 1}.`,
  startMinute: 9 * 60 + index * 5,
  endMinute: 9 * 60 + (index + 1) * 5,
}));

const quieterDays: Array<ReadonlyArray<DayTimelineEvent>> = [
  [
    {
      id: "morning-booking",
      kind: "booking",
      privacy: "full",
      title: "Confocal microscope",
      bookedBy: "ada.lovelace",
      item: { name: "Confocal microscope", globalId: "IN123" },
      canEdit: false,
      notes: "Morning imaging session.",
      startMinute: 9 * 60 + 30,
      endMinute: 11 * 60,
    },
  ],
  [
    {
      id: "maintenance",
      kind: "blockout",
      title: "Scheduled maintenance",
      item: { name: "Confocal microscope", globalId: "IN123" },
      notes: "Laser alignment and inspection.",
      startMinute: 13 * 60,
      endMinute: 15 * 60,
    },
  ],
];

function SynchronizedDayRows() {
  const [viewState, setViewState] = React.useState<DayTimelineViewState>({ zoom: 1, centerMinute: 13 * 60 });
  const rows = [denseBookings, ...quieterDays];
  return (
    <div className="space-y-8">
      {rows.map((events, index) => (
        <DayTimeline
          key={index}
          date={`2026-07-${String(22 + index).padStart(2, "0")}`}
          timezone="Europe/Berlin"
          events={events}
          startWindow={8 * 60}
          endWindow={18 * 60}
          nowMinute={index === 0 ? 14 * 60 + 37 : undefined}
          viewState={viewState}
          onViewStateChange={setViewState}
          showZoomControls={index === 0}
          hourWidth={176}
        />
      ))}
    </div>
  );
}

const meta = {
  title: "Booking/DayTimeline",
  component: DayTimeline,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking"]}>
        <div className="min-h-screen bg-background p-4 sm:p-8">
          <Story />
        </div>
      </I18nRoot>
    ),
  ],
  args: {
    date: "2026-07-22",
    timezone: "Europe/Berlin",
    events: denseBookings,
    startWindow: 8 * 60,
    endWindow: 18 * 60,
    nowMinute: 14 * 60 + 37,
  },
} satisfies Meta<typeof DayTimeline>;

export default meta;

type Story = StoryObj<typeof meta>;

export const DensityAwareEqualHourWidths: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByTestId("day-timeline-canvas");
    const eventBars = Array.from(canvasElement.querySelectorAll("[data-event-id]"));
    expect(eventBars).toHaveLength(12);
    expect(new Set(eventBars.map((event) => event.getAttribute("data-lane"))).size).toBe(3);
    expect(eventBars[0].querySelector("[data-event-time]")).toHaveTextContent("09:00");
    expect(eventBars[11].querySelector("[data-event-time]")).toHaveTextContent("09:55");
  },
};

export const DensityWithoutCompactCards: Story = {
  args: {
    compactCards: false,
    hourWidth: 480,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByTestId("day-timeline-canvas");
    const eventBars = Array.from(canvasElement.querySelectorAll<HTMLElement>("[data-event-id]"));
    for (const eventBar of eventBars) expect(eventBar.getBoundingClientRect().width).toBeCloseTo(120, 0);
    expect(eventBars[0].querySelector("[data-event-time]")).toHaveTextContent("09:00–09:05");
    expect(eventBars[11].querySelector("[data-event-time]")).toHaveTextContent("09:55–10:00");
  },
};

export const Sub15MinuteEvents: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const timelineCanvas = await canvas.findByTestId("day-timeline-canvas");
    const eventBars = Array.from(canvasElement.querySelectorAll<HTMLElement>("[data-event-id]"));
    for (const eventBar of eventBars) {
      expect(eventBar.getBoundingClientRect().width).toBeCloseTo(44, 0);
      const card = eventBar.querySelector<HTMLElement>("article");
      const time = eventBar.querySelector<HTMLElement>("[data-event-time]");
      const eventDate = eventBar.querySelector<HTMLElement>("[data-event-date]");
      const timeRange = eventBar.querySelector<HTMLElement>("[data-event-time-range]");
      expect(card).not.toBeNull();
      expect(time).not.toBeNull();
      expect(eventDate).toHaveTextContent("22-07-2026");
      expect(timeRange).toHaveTextContent(/\d{2}:\d{2} - \d{2}:\d{2}/);
      if (!card || !time || !eventDate || !timeRange) throw new Error("Timeline card content is missing");
      expect(card).toHaveAttribute("title");
      expect(time.getBoundingClientRect().top).toBeGreaterThan(card.getBoundingClientRect().top);
      expect(time.getBoundingClientRect().bottom).toBeLessThanOrEqual(card.getBoundingClientRect().bottom);
      expect(Number.parseFloat(getComputedStyle(time).lineHeight)).toBeGreaterThanOrEqual(12);
    }
    const heightBeforeOpening = timelineCanvas.getBoundingClientRect().height;
    const firstDetails = canvas.getByRole("button", {
      name: /show details for Confocal microscope · researcher\.01, 09:00–09:05/i,
    });
    await userEvent.click(firstDetails);
    expect(firstDetails).toHaveAttribute("aria-expanded", "true");
    expect(timelineCanvas.getBoundingClientRect().height).toBeCloseTo(heightBeforeOpening, 0);
    const popover = within(document.body);
    expect(popover.getByText("09:00–09:05")).toBeInTheDocument();
    expect(popover.getByText("Sample check 1.")).toBeInTheDocument();
    await userEvent.click(firstDetails);
    expect(firstDetails).toHaveAttribute("aria-expanded", "false");
    await waitFor(() => expect(popover.queryByRole("dialog", { name: "09:00–09:05" })).not.toBeInTheDocument());
  },
};

export const NowBeforeWindow: Story = {
  args: { nowMinute: 7 * 60 + 15 },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByTestId("day-timeline-canvas");
    expect(canvas.getByTestId("day-timeline-now")).toHaveAttribute("data-edge", "before");
    const nowLabel = canvas.getByText("Now 07:15");
    expect(canvas.queryByText(/before visible window/i)).not.toBeInTheDocument();
    expect(nowLabel.getBoundingClientRect().bottom).toBeLessThan(canvas.getByText("00:00").getBoundingClientRect().top);
  },
};

export const ZoomPreservesViewportPosition: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByTestId("day-timeline-canvas");
    const scroller = canvas.getByTestId("day-timeline-scroller");
    const zoomIn = canvas.getByRole("button", { name: "Zoom in" });
    const zoomOut = canvas.getByRole("button", { name: "Zoom out" });
    expect(zoomOut).toBeDisabled();
    scroller.scrollLeft = 0;
    const centerBeforeZoom = ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * 24 * 60;
    await userEvent.click(zoomIn);
    const centerAfterZoom = ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * 24 * 60;
    expect(centerAfterZoom).toBeCloseTo(centerBeforeZoom, 0);
    for (let step = 0; step < 5; step += 1) await userEvent.click(zoomIn);
    expect(zoomIn).toBeDisabled();
    expect(canvas.queryByText(/\d+%/)).not.toBeInTheDocument();
  },
};

export const SynchronizedRows: Story = {
  render: () => <SynchronizedDayRows />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const scrollers = await canvas.findAllByTestId("day-timeline-scroller");
    const canvases = canvas.getAllByTestId("day-timeline-canvas");
    expect(scrollers).toHaveLength(3);
    scrollers[0].scrollLeft += 300;
    fireEvent.scroll(scrollers[0]);
    await waitFor(() => {
      expect(scrollers[1].scrollLeft).toBeCloseTo(scrollers[0].scrollLeft, 0);
      expect(scrollers[2].scrollLeft).toBeCloseTo(scrollers[0].scrollLeft, 0);
    });
    const widthsBeforeZoom = canvases.map(({ style }) => style.width);
    await userEvent.click(canvas.getByRole("button", { name: "Zoom in" }));
    await waitFor(() => {
      expect(canvases.map(({ style }) => style.width)).not.toEqual(widthsBeforeZoom);
    });
    await waitFor(() => {
      expect(new Set(canvases.map(({ style }) => style.width))).toHaveLength(1);
    });
  },
};

export const ExpandedEventKinds: Story = {
  render: () => <DayTimelineStory />,
};
