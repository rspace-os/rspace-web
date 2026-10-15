import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { createBookingEventStoryQueryClient, createBookingEventTestRouter } from "./__tests__/bookingEventTestHarness";

function BookingEventPageStory({ edit = false }: { edit?: boolean }) {
  const queryClient = createBookingEventStoryQueryClient();
  const router = createBookingEventTestRouter(`/booking/calendar/bookings/41${edit ? "/edit" : ""}`);

  return (
    <QueryClientProvider client={queryClient}>
      <I18nRoot namespaces={["booking", "common"]}>
        <RouterProvider router={router as never} />
      </I18nRoot>
    </QueryClientProvider>
  );
}

const meta = {
  title: "Booking/Pages/Booking Event",
  component: BookingEventPageStory,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof BookingEventPageStory>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Confirmed: Story = {};

export const DirectEdit: Story = { args: { edit: true } };
