import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { type BookingEventPeriod, fetchBookableItemEvents } from "./bookableItemEvents";

type BookingEventListProps = {
  globalId: string;
  timezone: string;
  period: BookingEventPeriod;
  cutoff: string;
};

function EventPagination({
  page,
  totalPages,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: {
  page: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  return (
    <nav aria-label={t("bookableItemDetails.events.pagination")} className="flex items-center justify-between">
      <Button type="button" variant="outline" disabled={!hasPrevious} onClick={onPrevious}>
        {commonT("actions.previous")}
      </Button>
      <span>{t("bookableItemDetails.events.page", { page, totalPages })}</span>
      <Button type="button" variant="outline" disabled={!hasNext} onClick={onNext}>
        {commonT("actions.next")}
      </Button>
    </nav>
  );
}

export function BookingEventList({ globalId, timezone, period, cutoff }: BookingEventListProps) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [page, setPage] = useState(0);

  useEffect(() => setPage(0), [globalId, period, cutoff]);

  const events = useQuery({
    queryKey: ["api-v2", "bookings", "bookable-item-events", globalId, period, cutoff, page],
    queryFn: ({ signal }) => fetchBookableItemEvents({ globalId, period, cutoff, page, token, signal }),
  });
  const formatter = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });

  if (events.isPending) {
    return <p role="status">{t("bookableItemDetails.events.loading")}</p>;
  }
  if (events.isError) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyTitle>{t("bookableItemDetails.events.error.title")}</EmptyTitle>
          <EmptyDescription>{t("bookableItemDetails.events.error.description")}</EmptyDescription>
        </EmptyHeader>
        <Button type="button" variant="outline" onClick={() => void events.refetch()}>
          {commonT("actions.retry")}
        </Button>
      </Empty>
    );
  }
  if (events.data.docs.length === 0) {
    return (
      <div className="space-y-4">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{t("bookableItemDetails.events.empty")}</EmptyTitle>
          </EmptyHeader>
        </Empty>
        {events.data.hasPrevPage ? (
          <EventPagination
            page={events.data.page}
            totalPages={events.data.totalPages}
            hasPrevious
            hasNext={false}
            onPrevious={() => setPage((current) => Math.max(0, current - 1))}
            onNext={() => undefined}
          />
        ) : null}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        {t("bookableItemDetails.events.count", { count: events.data.totalDocs })}
      </p>
      <ol className="space-y-3">
        {events.data.docs.map((booking) => (
          <li key={booking.id}>
            <Card size="sm">
              <CardHeader>
                <CardTitle>
                  {booking.privacy === "busy" ? t("bookableItemDetails.events.busy") : booking.bookedBy}
                </CardTitle>
                {booking.canEdit ? (
                  <CardAction>
                    <Link
                      className={buttonVariants({ size: "sm", variant: "outline" })}
                      to="/booking/calendar/bookings/$id"
                      params={{ id: String(booking.id) }}
                    >
                      {t("bookableItemDetails.events.edit")}
                    </Link>
                  </CardAction>
                ) : null}
              </CardHeader>
              <CardContent>
                <time dateTime={booking.start}>
                  {formatter.formatRange(new Date(booking.start), new Date(booking.end))}
                </time>
                {booking.privacy === "full" ? (
                  <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                    <dt>{t("bookableItemDetails.events.requester")}</dt>
                    <dd>{booking.bookedBy}</dd>
                    {booking.purpose === null ? null : (
                      <>
                        <dt>{t("bookableItemDetails.events.purpose")}</dt>
                        <dd>{booking.purpose}</dd>
                      </>
                    )}
                  </dl>
                ) : null}
              </CardContent>
            </Card>
          </li>
        ))}
      </ol>
      <EventPagination
        page={events.data.page}
        totalPages={events.data.totalPages}
        hasPrevious={events.data.hasPrevPage}
        hasNext={events.data.hasNextPage}
        onPrevious={() => setPage((current) => Math.max(0, current - 1))}
        onNext={() => setPage((current) => current + 1)}
      />
    </div>
  );
}
