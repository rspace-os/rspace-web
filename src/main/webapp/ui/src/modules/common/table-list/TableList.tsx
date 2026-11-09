import { TableListLayout } from "./components/TableListLayout";
import type { TableListProps } from "./types";

export { TableListAlertItem } from "./components/TableListAlertItem";
export { type TableListAlert, type TableListAlertsApi, useTableListAlerts } from "./components/TableListAlerts";
export type {
  TableListFilterButtons,
  TableListPresentations,
  TableListPresentationVisibility,
  TableListProps,
  TableListRowActions,
  TableListSelection,
  TableListSelectionContext,
} from "./types";

export function TableList<TDocument extends Record<string, unknown>>(props: TableListProps<TDocument>) {
  return <TableListLayout {...props} />;
}
