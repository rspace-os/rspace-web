import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Profiler, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { sliceAcrossZonedDay } from "@/modules/booking/domain/bookingTime";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { DayTimeline, DayTimelineEventCard, type DayTimelineViewState } from "./DayTimeline";
import { useDayTimelineScrollSync } from "./useDayTimelineScrollSync";

describe("DayTimeline table row", () => {
  it("synchronizes differently sized rows in either direction without React renders or scroll echoes", async () => {
    const onRender = vi.fn();
    function Rows() {
      const [viewState, setViewState] = useState<DayTimelineViewState>({ zoom: 1, centerMinute: 780 });
      const scrollSync = useDayTimelineScrollSync();
      return (
        <Profiler id="timelines" onRender={onRender}>
          {["First", "Second"].map((itemName) => (
            <DayTimeline
              key={itemName}
              itemName={itemName}
              date="2026-09-06"
              timezone="Europe/Berlin"
              events={[]}
              startWindow={480}
              endWindow={1080}
              viewState={viewState}
              onViewStateChange={setViewState}
              scrollSync={scrollSync}
            />
          ))}
        </Profiler>
      );
    }
    render(<Rows />);
    const scrollers = screen.getAllByTestId("day-timeline-scroller");
    for (const [index, scroller] of scrollers.entries()) {
      Object.defineProperties(scroller, {
        scrollWidth: { value: index === 0 ? 2400 : 3000, configurable: true },
        clientWidth: { value: 600, configurable: true },
      });
    }
    onRender.mockClear();
    scrollers[0].scrollLeft = 400;
    fireEvent.scroll(scrollers[0]);
    await waitFor(() => expect(scrollers[1].scrollLeft).toBeCloseTo(575));
    fireEvent.scroll(scrollers[1]);
    expect(scrollers[0].scrollLeft).toBe(400);
    scrollers[1].scrollLeft = 1200;
    fireEvent.scroll(scrollers[1]);
    await waitFor(() => expect(scrollers[0].scrollLeft).toBeCloseTo(900));
    expect(onRender).not.toHaveBeenCalled();
  });

  it("reuses the expandable event card outside the timeline", async () => {
    const user = userEvent.setup();
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });

    render(
      <DayTimelineEventCard
        date="2026-08-17"
        event={{
          id: "flow-card",
          kind: "booking",
          privacy: "full",
          title: "Confocal microscope",
          bookedBy: "Ada",
          item: {
            name: "Confocal microscope",
            globalId: "IN123",
            location: { name: "Imaging lab", globalId: "IC123" },
          },
          canEdit: false,
          notes: "Cell imaging",
          startMinute: 480,
          endMinute: 540,
        }}
      />,
      { wrapper },
    );

    expect(screen.getByText("17-08-2026")).toBeVisible();
    expect(screen.getByText("08:00 - 09:00")).toBeVisible();
    const details = screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ });
    details.focus();
    await user.keyboard("{Enter}");

    expect(details).toHaveAttribute("aria-expanded", "true");
    const popup = screen.getByRole("dialog", { name: "08:00–09:00" });
    expect(within(popup).getByText("17-08-2026")).toBeVisible();
    expect(within(popup).queryByText("Start")).not.toBeInTheDocument();
    expect(within(popup).queryByText("End")).not.toBeInTheDocument();
    expect(within(popup).getByText("Cell imaging")).toBeVisible();
    expect(within(popup).getByText("Ada")).toBeVisible();
    expect(within(popup).getByRole("link", { name: "Open inventory record IN123" })).toHaveAttribute(
      "href",
      "/globalId/IN123",
    );
    expect(within(popup).getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC123");
  });

  it("shows both dates and day offsets for an event crossing the displayed day", async () => {
    const user = userEvent.setup();
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });

    render(
      <DayTimelineEventCard
        date="2026-08-17"
        event={{
          id: "overnight-card",
          kind: "booking",
          privacy: "full",
          title: "Overnight acquisition",
          bookedBy: "Ada",
          item: { name: "Confocal microscope", globalId: "IN123" },
          canEdit: false,
          notes: "Long-running acquisition",
          startMinute: -30,
          endMinute: 24 * 60 + 90,
        }}
      />,
      { wrapper },
    );

    expect(screen.getByText("16-08-2026 - 18-08-2026")).toBeVisible();
    expect(screen.getByText("23:30 (-1) - 01:30 (+1)")).toBeVisible();
    const details = screen.getByRole("button", {
      name: /Show details for Overnight acquisition · Ada, 23:30 \(-1\)–01:30 \(\+1\)/,
    });
    await user.click(details);

    const popup = screen.getByRole("dialog", { name: "23:30 (-1)–01:30 (+1)" });
    expect(within(popup).getByText("Start")).toBeVisible();
    expect(within(popup).getByText("16-08-2026 · 23:30 (-1)")).toBeVisible();
    expect(within(popup).getByText("End")).toBeVisible();
    expect(within(popup).getByText("18-08-2026 · 01:30 (+1)")).toBeVisible();
    expect(within(popup).getByText("26 hours")).toBeVisible();
  });

  it("uses the item name in its accessible label and hides detail controls", async () => {
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });

    render(
      <DayTimeline
        date="2026-08-17"
        timezone="Europe/Berlin"
        events={[]}
        startWindow={0}
        endWindow={24 * 60}
        itemName="Confocal microscope"
        variant="table-row"
      />,
      { wrapper },
    );

    expect(screen.getByRole("region", { name: /Confocal microscope.*Europe\/Berlin/i })).toBeVisible();
    expect(screen.queryByRole("group", { name: "Timeline zoom" })).not.toBeInTheDocument();
  });

  it("closes event details when another card or the surrounding page is clicked", async () => {
    const user = userEvent.setup();
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });
    render(
      <DayTimeline
        date="2026-08-17"
        timezone="Europe/Berlin"
        events={[
          {
            id: "editable",
            kind: "booking",
            privacy: "full",
            title: "Confocal microscope",
            bookedBy: "Ada",
            item: { name: "Confocal microscope", globalId: "IN123" },
            canEdit: true,
            startMinute: 480,
            endMinute: 540,
          },
          {
            id: "readonly",
            kind: "booking",
            privacy: "full",
            title: "Electron microscope",
            bookedBy: "Grace",
            item: { name: "Electron microscope", globalId: "IN124" },
            canEdit: false,
            startMinute: 600,
            endMinute: 660,
          },
          { id: "busy", kind: "booking", privacy: "busy", startMinute: 720, endMinute: 780 },
          {
            id: "blockout",
            kind: "blockout",
            title: "Maintenance",
            item: { name: "Flow cytometer", globalId: "IN125" },
            createdBy: "Morgan Ellis (mellis)",
            notes: "Laser alignment",
            startMinute: 840,
            endMinute: 900,
          },
        ]}
        startWindow={0}
        endWindow={24 * 60}
        renderEventActions={(event) => <button type="button">{`Manage ${event.id}`}</button>}
        renderBlockoutActions={(event) => <button type="button">{`Manage ${event.id}`}</button>}
      />,
      { wrapper },
    );

    expect(screen.queryByRole("button", { name: "Manage editable" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ }));
    expect(screen.getByRole("button", { name: "Manage editable" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: /Show details for Electron microscope · Grace/ }));
    expect(screen.getByRole("button", { name: "Manage readonly" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Manage editable" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Zoom in" }));
    expect(screen.queryByRole("button", { name: "Manage readonly" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ }));
    const editablePopup = screen.getByRole("dialog", { name: "08:00–09:00" });
    await user.click(within(editablePopup).getByRole("button", { name: /Hide details for Confocal microscope · Ada/ }));
    expect(screen.queryByRole("button", { name: "Manage editable" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ })).toHaveFocus();

    await user.click(screen.getByRole("button", { name: /Show details for Busy/ }));
    expect(screen.getByRole("button", { name: "Manage busy" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: /Show details for Maintenance/ }));
    expect(screen.queryByRole("button", { name: "Manage busy" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Manage blockout" })).toBeVisible();
    const blockoutPopup = screen.getByRole("dialog", { name: "14:00–15:00" });
    expect(within(blockoutPopup).getByText("Laser alignment")).toBeVisible();
    expect(within(blockoutPopup).getByText("Morgan Ellis (mellis)")).toBeVisible();
    expect(within(blockoutPopup).queryByText("Booked by")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Zoom out" }));
    expect(screen.queryByRole("button", { name: "Manage blockout" })).not.toBeInTheDocument();
  });

  it("renders clock-change bookings with distinct offsets and selectable elapsed positions", async () => {
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });
    const onRangeSelect = vi.fn();
    render(
      <DayTimeline
        date="2026-10-25"
        timezone="Europe/Berlin"
        startWindow={0}
        endWindow={1440}
        onRangeSelect={onRangeSelect}
        events={[
          {
            id: "repeated",
            kind: "booking",
            privacy: "full",
            title: "Repeated hour",
            bookedBy: "Ada",
            canEdit: false,
            item: { name: "Scope", globalId: "IN123" },
            ...sliceAcrossZonedDay("2026-10-25T00:45:00Z", "2026-10-25T01:15:00Z", "2026-10-25", "Europe/Berlin"),
          },
          { id: "late", kind: "booking", privacy: "busy", startMinute: 1470, endMinute: 1500 },
        ]}
      />,
      { wrapper },
    );
    expect(screen.getByRole("article", { name: /Repeated hour · Ada, 02:45 \+02:00–02:15 \+01:00/ })).toBeVisible();
    expect(screen.getByRole("article", { name: /Busy, 23:30 \+01:00–00:00 \+01:00/ })).toBeVisible();
    expect(screen.getByText("02:00 +02:00")).toBeVisible();
    expect(screen.getByText("02:00 +01:00")).toBeVisible();
    await userEvent.setup().click(screen.getByRole("button", { name: /Show details for Repeated hour/ }));
    expect(within(screen.getByRole("dialog")).getByText("30 minutes")).toBeVisible();
    await userEvent.setup().keyboard("{Escape}");
    const canvas = screen.getByTestId("day-timeline-canvas");
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 1500,
      bottom: 100,
      width: 1500,
      height: 100,
      toJSON: () => ({}),
    });
    fireEvent.pointerDown(canvas, { clientX: 165, pointerId: 1 });
    fireEvent.pointerMove(canvas, { clientX: 195, pointerId: 1 });
    expect(screen.getByText("02:45 +02:00–02:15 +01:00")).toBeVisible();
    fireEvent.pointerUp(canvas, { clientX: 195, pointerId: 1 });
    expect(onRangeSelect).toHaveBeenCalledWith({ startMinute: 165, endMinute: 195 }, expect.any(HTMLElement));
  });

  it("snaps drag selection to the resource increment and disables creation while leased", async () => {
    const wrapper = await createRealI18nWrapper({
      resources: { booking: bookingEnglish, common: commonEnglish },
      defaultNS: "common",
    });
    const onRangeSelect = vi.fn();
    const { rerender } = render(
      <DayTimeline
        date="2026-08-17"
        timezone="Europe/Berlin"
        events={[]}
        startWindow={0}
        endWindow={24 * 60}
        variant="table-row"
        snapIncrementMinutes={15}
        onRangeSelect={onRangeSelect}
      />,
      { wrapper },
    );
    const canvas = screen.getByTestId("day-timeline-canvas");
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 1_440,
      bottom: 100,
      width: 1_440,
      height: 100,
      toJSON: () => ({}),
    });

    fireEvent.pointerDown(canvas, { clientX: 481, pointerId: 1 });
    fireEvent.pointerMove(canvas, { clientX: 539, pointerId: 1 });
    expect(screen.getByText("08:00–09:00")).toBeVisible();
    fireEvent.pointerUp(canvas, { clientX: 539, pointerId: 1 });
    expect(onRangeSelect).toHaveBeenCalledWith({ startMinute: 480, endMinute: 540 }, expect.any(HTMLElement));

    rerender(
      <DayTimeline
        date="2026-08-17"
        timezone="Europe/Berlin"
        events={[]}
        startWindow={0}
        endWindow={24 * 60}
        variant="table-row"
        snapIncrementMinutes={15}
        creationDisabled
        onRangeSelect={onRangeSelect}
      />,
    );
    fireEvent.pointerDown(screen.getByTestId("day-timeline-canvas"), { clientX: 600, pointerId: 2 });
    fireEvent.pointerUp(screen.getByTestId("day-timeline-canvas"), { clientX: 660, pointerId: 2 });
    expect(onRangeSelect).toHaveBeenCalledTimes(1);
  });
});
