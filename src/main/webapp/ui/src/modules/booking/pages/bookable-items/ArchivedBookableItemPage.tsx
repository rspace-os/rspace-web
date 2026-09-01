import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { DeleteBookingDialog } from "@/modules/booking/pages/bookings/DeleteBookingDialog";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { Button } from "@/modules/common/ui/button";

const ArchivedSummarySchema = v.object({
  id: v.number(),
  version: v.number(),
  target: v.nullable(v.object({ globalId: v.string(), name: v.string() })),
  canUnarchive: v.boolean(),
  canCancelBookings: v.boolean(),
  calendarSubscriptionActive: v.literal(false),
  futureBookings: v.array(
    v.object({
      id: v.number(),
      start: v.pipe(v.string(), v.isoTimestamp()),
      end: v.pipe(v.string(), v.isoTimestamp()),
      version: v.number(),
      canEdit: v.literal(false),
      canCancel: v.boolean(),
    }),
  ),
});

async function fetchArchivedSummary(id: number, token: string, signal?: AbortSignal) {
  const response = await fetch(`/api/v2/booking-configurations/${id}/archived-summary`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Archived booking configuration request failed (${response.status})`);
  return parseOrThrow(ArchivedSummarySchema, await response.json());
}

async function unarchive(id: number, version: number, token: string) {
  const response = await fetch(`/api/v2/booking-configurations/${id}/unarchive`, {
    method: "POST",
    headers: bookingApiV2Headers(token, { "If-Match": `"${version}"` }),
  });
  if (!response.ok) throw new Error(`Unarchive request failed (${response.status})`);
}

export default function ArchivedBookableItemPage() {
  const { t } = useTranslation("booking");
  const { id } = useParams({ from: "/booking/bookable-items/archived/$id" });
  const navigate = useNavigate({ from: "/booking/bookable-items/archived/$id" });
  const configurationId = Number(id);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const queryClient = useQueryClient();
  const summary = useQuery({
    queryKey: ["api-v2", "booking-configurations", configurationId, "archived-summary"],
    queryFn: ({ signal }) => fetchArchivedSummary(configurationId, token, signal),
    retry: false,
  });
  const restore = useMutation({
    mutationFn: () => unarchive(configurationId, summary.data?.version ?? -1, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      if (summary.data?.target) {
        await navigate({
          to: "/booking/bookable-items/$globalId",
          params: { globalId: summary.data.target.globalId },
        });
      } else {
        await navigate({ to: "/booking" });
      }
    },
  });

  if (summary.isPending)
    return (
      <main className="p-8" aria-busy="true">
        <p role="status">{t("archived.loading")}</p>
      </main>
    );
  if (summary.isError || !summary.data)
    return (
      <main className="p-8">
        <p role="alert">{t("archived.error")}</p>
      </main>
    );

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">{summary.data.target?.name ?? t("archived.unknownItem")}</h1>
        <p>{t("archived.description")}</p>
        <p className="text-sm text-muted-foreground">{t("archived.subscriptionInactive")}</p>
      </header>
      {summary.data.canUnarchive ? (
        <div>
          <Button
            type="button"
            disabled={restore.isPending}
            aria-busy={restore.isPending}
            onClick={() => restore.mutate()}
          >
            {t("archived.unarchive")}
          </Button>
          {restore.isError ? (
            <p role="alert" className="mt-2 text-sm text-destructive">
              {t("archived.unarchiveError")}
            </p>
          ) : null}
        </div>
      ) : null}
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">{t("archived.futureBookings")}</h2>
        {summary.data.futureBookings.length === 0 ? (
          <p>{t("archived.noFutureBookings")}</p>
        ) : (
          <ul className="space-y-2">
            {summary.data.futureBookings.map((booking) => (
              <li key={booking.id} className="flex flex-wrap items-center justify-between gap-3 rounded-sm border p-3">
                <span>{formatAgendaPeriod(booking.start, booking.end, preferences.timeZone)}</span>
                {booking.canCancel && summary.data.target ? (
                  <DeleteBookingDialog
                    bookingId={booking.id}
                    bookingVersion={booking.version}
                    itemName={summary.data.target.name}
                    period={formatAgendaPeriod(booking.start, booking.end, preferences.timeZone)}
                    token={token}
                    onDeleted={async () => {
                      await summary.refetch();
                    }}
                  />
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
