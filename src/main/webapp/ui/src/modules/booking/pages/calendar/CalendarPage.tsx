import * as React from "react";
import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { ResourceScheduleSkeleton } from "./BookingEventsCalendar";
import { CalendarContent } from "./CalendarContent";

export default function CalendarPage() {
  return (
    <React.Suspense fallback={<CalendarSkeleton />}>
      <CalendarContent />
    </React.Suspense>
  );
}

function CalendarSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-5">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-24 w-full" />
        <ResourceScheduleSkeleton />
      </div>
    </main>
  );
}
