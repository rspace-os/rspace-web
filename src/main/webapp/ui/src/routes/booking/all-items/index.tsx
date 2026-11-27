import { createFileRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "@/modules/booking/pages/all-bookable-items/AllBookableItemsPage";
import { allBookableItemsTable } from "@/modules/booking/pages/all-bookable-items/allBookableItemsTableConfig";
import { tableViewRouteOptions } from "@/modules/common/table-list/tableViewRoute";

export const Route = createFileRoute("/booking/all-items/")({
  ...tableViewRouteOptions([allBookableItemsTable]),
  component: AllBookableItemsPage,
});
