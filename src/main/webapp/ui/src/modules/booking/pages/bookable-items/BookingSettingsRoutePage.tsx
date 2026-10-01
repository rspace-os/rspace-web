import { QueryErrorResetBoundary } from "@tanstack/react-query";
import { CatchBoundary, Link } from "@tanstack/react-router";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { cn } from "@/modules/common/utils/cn";
import { isPermanentClientError } from "../queryRetry";
import BookingSettingsPage, { SettingsSkeleton } from "./BookingSettingsPage";

/** Shown in place of the institution-wide settings to users who cannot change them. */
export function BookingSettingsNotPermitted() {
  const { t } = useTranslation("booking");
  return (
    <main className="p-4 sm:p-8">
      <Empty className="border">
        <EmptyHeader>
          <EmptyTitle>{t("settings.notPermitted.title")}</EmptyTitle>
          <EmptyDescription>{t("settings.notPermitted.description")}</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Link to="/booking/preferences" className={cn(buttonVariants({ variant: "outline" }), "rounded-sm")}>
            {t("settings.notPermitted.action")}
          </Link>
        </EmptyContent>
      </Empty>
    </main>
  );
}

function BookingSettingsUnavailable({ reset }: { reset: () => void }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  return (
    <main className="p-4 sm:p-8">
      <Empty className="border">
        <EmptyHeader>
          <EmptyTitle>{t("settings.unavailable.title")}</EmptyTitle>
          <EmptyDescription>{t("settings.unavailable.description")}</EmptyDescription>
        </EmptyHeader>
        <Button type="button" variant="outline" onClick={reset}>
          {commonT("actions.retry")}
        </Button>
      </Empty>
    </main>
  );
}

function GuardedBookingSettings() {
  const { data: currentUser } = useCurrentUserQuery();
  // The settings API is sysadmin-only; asking it as anyone else only produces a 403.
  if (!currentUser.hasSysAdminRole) return <BookingSettingsNotPermitted />;
  return (
    // A load failure stays inside the Booking layout instead of replacing the whole app.
    <QueryErrorResetBoundary>
      {({ reset: resetQueryErrors }) => (
        <CatchBoundary
          getResetKey={() => "booking-settings"}
          errorComponent={({ error, reset }) =>
            isPermanentClientError(error) ? (
              <BookingSettingsNotPermitted />
            ) : (
              <BookingSettingsUnavailable
                reset={() => {
                  resetQueryErrors();
                  reset();
                }}
              />
            )
          }
        >
          <BookingSettingsPage />
        </CatchBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}

/** The Settings route: institution-wide booking defaults for sysadmins, a not-permitted state for others. */
export default function BookingSettingsRoutePage() {
  return (
    <Suspense fallback={<SettingsSkeleton />}>
      <GuardedBookingSettings />
    </Suspense>
  );
}
