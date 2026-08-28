import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { DayTimeline, DayTimelineEventCard } from "./DayTimeline";

describe("DayTimeline table row", () => {
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
          title: "Confocal microscope · Ada",
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

    const details = screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ });
    await user.click(details);

    expect(details).toHaveAttribute("aria-expanded", "true");
    const popup = screen.getByRole("dialog", { name: "08:00–09:00" });
    expect(within(popup).getByText("Cell imaging")).toBeVisible();
    expect(within(popup).getByText("Ada")).toBeVisible();
    expect(within(popup).getByRole("link", { name: "Open inventory record IN123" })).toHaveAttribute(
      "href",
      "/globalId/IN123",
    );
    expect(within(popup).getByRole("link", { name: "Imaging lab" })).toHaveAttribute("href", "/globalId/IC123");
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

  it("keeps full-booking popups independent and preserves event-kind action rules", async () => {
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
            title: "Confocal microscope · Ada",
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
            title: "Electron microscope · Grace",
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
            notes: "Laser alignment",
            startMinute: 840,
            endMinute: 900,
          },
        ]}
        startWindow={0}
        endWindow={24 * 60}
        renderEventActions={(event) => <button type="button">{`Manage ${event.id}`}</button>}
      />,
      { wrapper },
    );

    expect(screen.queryByRole("button", { name: "Manage editable" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Show details for Confocal microscope · Ada/ }));
    expect(screen.getByRole("button", { name: "Manage editable" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: /Show details for Electron microscope · Grace/ }));
    expect(screen.getByRole("button", { name: "Manage readonly" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Manage editable" })).toBeVisible();

    const gracePopup = screen.getByRole("dialog", { name: "10:00–11:00" });
    await user.click(within(gracePopup).getByRole("button", { name: /Hide details for Electron microscope · Grace/ }));
    expect(screen.queryByRole("button", { name: "Manage readonly" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Manage editable" })).toBeVisible();
    expect(screen.getByRole("button", { name: /Show details for Electron microscope · Grace/ })).toHaveFocus();

    await user.click(screen.getByRole("button", { name: /Show details for Busy/ }));
    expect(screen.getByRole("button", { name: "Manage busy" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: /Show details for Maintenance/ }));
    expect(screen.queryByRole("button", { name: "Manage blockout" })).not.toBeInTheDocument();
    const blockoutPopup = screen.getByRole("dialog", { name: "14:00–15:00" });
    expect(within(blockoutPopup).getByText("Laser alignment")).toBeVisible();
    expect(within(blockoutPopup).queryByText("Booked by")).not.toBeInTheDocument();
  });
});
