import { render, screen } from "@testing-library/react";
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
        event={{
          id: "flow-card",
          kind: "booking",
          privacy: "full",
          bookedBy: "Ada",
          canEdit: false,
          notes: "Cell imaging",
          startMinute: 480,
          endMinute: 540,
        }}
      />,
      { wrapper },
    );

    const details = screen.getByRole("button", { name: /Show details for Ada/ });
    await user.click(details);

    expect(details).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Cell imaging")).toBeVisible();
    expect(screen.getByText("08:00–09:00")).toBeVisible();
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

  it("shows supplied actions only after an editable full booking is expanded", async () => {
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
            bookedBy: "Ada",
            canEdit: true,
            startMinute: 480,
            endMinute: 540,
          },
          {
            id: "readonly",
            kind: "booking",
            privacy: "full",
            bookedBy: "Grace",
            canEdit: false,
            startMinute: 600,
            endMinute: 660,
          },
          { id: "busy", kind: "booking", privacy: "busy", startMinute: 720, endMinute: 780 },
          { id: "blockout", kind: "blockout", title: "Maintenance", startMinute: 840, endMinute: 900 },
        ]}
        startWindow={0}
        endWindow={24 * 60}
        renderEventActions={(event) => <button type="button">{`Manage ${event.bookedBy}`}</button>}
      />,
      { wrapper },
    );

    expect(screen.queryByRole("button", { name: "Manage Ada" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Show details for Ada/ }));
    expect(screen.getByRole("button", { name: "Manage Ada" })).toBeVisible();

    for (const name of [/Show details for Grace/, /Show details for Busy/, /Show details for Maintenance/]) {
      await user.click(screen.getByRole("button", { name }));
    }
    expect(screen.queryByRole("button", { name: "Manage Grace" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Manage Busy" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Manage Maintenance" })).not.toBeInTheDocument();
  });
});
