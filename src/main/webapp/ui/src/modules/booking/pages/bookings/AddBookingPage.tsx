import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { useBookableItem } from "@/modules/booking/components/BookableItemPicker";
import { ApiV2ProblemError, createBooking } from "@/modules/booking/domain/booking";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Heading } from "@/modules/common/ui/typography";
import { BookingForm, type BookingFormSubmission } from "./BookingForm";

function problemKey(
  error: unknown,
):
  | "bookings.errors.generic"
  | "bookings.errors.endAfterStart"
  | "bookings.errors.duration"
  | "bookings.errors.maximumDuration"
  | "bookings.errors.overlap"
  | "bookings.errors.granularity"
  | "bookings.errors.openingHours"
  | "bookings.errors.targetUnavailable"
  | "bookings.errors.forbidden" {
  if (!(error instanceof ApiV2ProblemError)) return "bookings.errors.generic";
  if (error.code === "errors.api.v2.booking.window") return "bookings.errors.endAfterStart";
  if (error.code === "errors.api.v2.booking.duration") return "bookings.errors.duration";
  if (error.code === "errors.api.v2.booking.maximumDuration") return "bookings.errors.maximumDuration";
  if (error.code === "errors.api.v2.booking.overlap") return "bookings.errors.overlap";
  if (error.code === "errors.api.v2.booking.granularity") return "bookings.errors.granularity";
  if (error.code === "errors.api.v2.booking.openingHours") return "bookings.errors.openingHours";
  if (error.code === "errors.api.v2.booking.target.unavailable") return "bookings.errors.targetUnavailable";
  if (error.code === "errors.api.v2.forbidden") return "bookings.errors.forbidden";
  return "bookings.errors.generic";
}

export default function AddBookingPage() {
  const { t } = useTranslation("booking");
  const search = useSearch({ from: "/booking/calendar/bookings/add" });
  const navigate = useNavigate({ from: "/booking/calendar/bookings/add" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const initialTarget = useBookableItem(search.target, token);
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (submission: BookingFormSubmission) =>
      createBooking(
        {
          target: { relationTo: "instruments", value: submission.target.targetId },
          start: submission.window.start,
          end: submission.window.end,
          purpose: submission.purpose,
        },
        token,
      ),
    onSuccess: async (booking, submission) => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", booking.id] });
      await navigate({
        to: "/booking/calendar",
        search: { date: search.date ?? submission.returnDate, target: submission.target.globalId },
      });
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.code === "errors.api.v2.booking.target.unavailable") {
        await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      }
    },
  });
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={2} as="h1">
        {t("bookings.addTitle")}
      </Heading>
      {search.target && !initialTarget.isPending && (initialTarget.isError || !initialTarget.data) && (
        <p role="alert">{t("bookings.errors.targetUnavailable")}</p>
      )}
      <BookingForm
        mode="add"
        initialTarget={initialTarget.data}
        initialDate={search.date}
        token={token}
        pending={mutation.isPending}
        error={mutation.error ? t(problemKey(mutation.error)) : undefined}
        onSubmit={(submission) => mutation.mutateAsync(submission)}
      />
    </main>
  );
}
