import { Outlet } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Heading } from "@/modules/common/ui/typography";

export function BookingDashboard() {
  const { t } = useTranslation("booking");
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("sidebar.dashboard")}
      </Heading>
    </main>
  );
}

export default function BookingPage() {
  return (
    <div className="mx-auto w-full max-w-7xl">
      <Outlet />
    </div>
  );
}
