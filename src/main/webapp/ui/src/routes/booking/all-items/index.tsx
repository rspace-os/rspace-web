import { createFileRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "@/modules/booking/pages/all-bookable-items/AllBookableItemsPage";
import { bookableItemsTable } from "@/modules/booking/pages/bookable-items/bookingConfigurationTable";
import { tableViewRouteOptions } from "@/modules/common/table-list/tableViewRoute";

export const Route = createFileRoute("/booking/all-items/")({
  ...tableViewRouteOptions([bookableItemsTable]),
  component: AllBookableItemsPage,
});
