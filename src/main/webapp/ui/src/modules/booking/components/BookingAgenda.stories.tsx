import type { Meta, StoryObj } from "@storybook/tanstack-react";
import type { BookingAgendaProps } from "@/modules/booking/components/BookingAgenda";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { BookingAgenda } from "./BookingAgenda";

const now = Date.parse("2026-09-24T09:00:00.000Z");

function booking(
  id: number,
  start: string,
  end: string,
  name: string | null,
  purpose: string,
  privacy: "full" | "busy" = "full",
  location?: string,
): BookingListDocument {
  const target: BookingListDocument["target"] = name
    ? {
        relationTo: "booking-instruments",
        value: {
          id: id + 100,
          name,
          deleted: false,
          parentContainerName: location ?? null,
          parentContainerGlobalId: null,
        },
        globalId: `IN${id + 100}`,
      }
    : null;

  return {
    id,
    version: 1,
    target,
    timezone: "Europe/Berlin",
    start,
    end,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy,
    purpose,
    bookedBy: null,
    createdBy: null,
    canViewConfiguration: privacy === "full",
    requesterId: 7,
    canEdit: false,
    canCancel: false,
    createdAt: start,
    updatedAt: start,
  };
}

const bookings = [
  booking(
    41,
    "2026-09-24T12:00:00.000Z",
    "2026-09-24T14:00:00.000Z",
    "Confocal microscope",
    "Live-cell imaging",
    "full",
    "Imaging suite · Room 204",
  ),
  booking(
    42,
    "2026-09-24T14:30:00.000Z",
    "2026-09-24T15:00:00.000Z",
    "high-resolution-liquid-chromatography-mass-spectrometer-with-an-extraordinarily-long-name",
    "Metabolomics batch 07 · repeat analysis",
    "full",
    "Analytical laboratory · Room 310, east wing",
  ),
  booking(
    43,
    "2026-09-25T08:00:00.000Z",
    "2026-09-25T09:30:00.000Z",
    "Flow cytometer",
    "Busy purpose stays private",
    "busy",
    "Core facility · Room 102",
  ),
  booking(44, "2026-09-25T13:00:00.000Z", "2026-09-25T14:00:00.000Z", null, "Scheduled analysis"),
  booking(
    45,
    "2026-09-25T20:00:00.000Z",
    "2026-09-26T00:00:00.000Z",
    "Cell culture room 2",
    "Overnight observation",
    "full",
    "Cell culture · Room 108",
  ),
  booking(
    46,
    "2026-10-25T00:30:00.000Z",
    "2026-10-25T01:30:00.000Z",
    "Plate reader",
    "Autumn time change check",
    "full",
    "Core facility · Room 102",
  ),
] satisfies readonly BookingListDocument[];

const meta = {
  title: "Booking/BookingAgenda",
  component: BookingAgenda,
  args: { bookings, timeZone: "Europe/Berlin", now },
} satisfies Meta<typeof BookingAgenda>;

export default meta;
type Story = StoryObj<typeof meta>;

function atWidth(width: number) {
  return (args: BookingAgendaProps) => (
    <I18nRoot namespaces={["booking", "common"]}>
      <div className="max-w-full border bg-background text-foreground" style={{ width }}>
        <BookingAgenda {...args} />
      </div>
    </I18nRoot>
  );
}

export const Narrow320: Story = { render: atWidth(320) };

export const Standard400: Story = { render: atWidth(400) };

export const Medium560: Story = { render: atWidth(560) };

export const Wide720: Story = { render: atWidth(720) };
