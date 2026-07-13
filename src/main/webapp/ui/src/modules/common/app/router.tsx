import { createRootRoute, createRouter } from "@tanstack/react-router";
import { createAboutRoute } from "@/modules/about/pages/AboutPage";
import { createBookingRoute } from "@/modules/booking/pages/BookingPage";
import NotFoundPage from "@/modules/common/pages/notFound/NotFoundPage";
import { createMaintenanceInProgressRoute } from "@/modules/maintenance/pages/MaintenanceInProgressPage";
import AppShell from "./AppShell";
import { LIGHTHOUSE_SCRIPT_ID, LIGHTHOUSE_SCRIPT_SRC } from "./lighthouse";

const rootRoute = createRootRoute({
  component: AppShell,
  notFoundComponent: NotFoundPage,
  head: () => ({
    meta: [{ title: "RSpace" }],
    scripts: [
      {
        id: LIGHTHOUSE_SCRIPT_ID,
        type: "text/javascript",
        async: true,
        defer: true,
        src: LIGHTHOUSE_SCRIPT_SRC,
      },
    ],
  }),
});

const bookingRoute = createBookingRoute(rootRoute);
const aboutRoute = createAboutRoute(rootRoute);
const maintenanceInProgressRoute = createMaintenanceInProgressRoute(rootRoute);

export const routeTree = rootRoute.addChildren([bookingRoute, aboutRoute, maintenanceInProgressRoute]);

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
