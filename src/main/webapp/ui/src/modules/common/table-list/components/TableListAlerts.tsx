import type { VariantProps } from "class-variance-authority";
import { createContext, type ReactNode, useContext } from "react";
import { useTranslation } from "react-i18next";
import type { alertToneVariants } from "@/modules/common/ui/alert";
import { TableListAlertItem } from "./TableListAlertItem";

/** A one-line result of a table action, shown above the table until the user dismisses it. */
export type TableListAlert = {
  /** Pushing an alert with an existing id replaces it. */
  id: string;
  message: ReactNode;
  actions?: ReactNode;
  tone?: NonNullable<VariantProps<typeof alertToneVariants>["variant"]>;
  icon?: ReactNode;
  undo?: {
    label?: string;
    run: () => Promise<unknown> | unknown;
    /** The row to focus once the undo lands, such as a row the undo brings back. */
    focusRowId?: string;
    /** Replaces the alert's message when the undo fails. */
    describeError?: (error: unknown) => string;
  };
};

export type TableListAlertsApi = {
  push: (...alerts: TableListAlert[]) => void;
  dismiss: (id: string) => void;
};

export type AlertEntry = TableListAlert & {
  renderKey: number;
  status: "idle" | "undoing" | "failed";
  failure?: string;
};

const TableListAlertsContext = createContext<TableListAlertsApi | null>(null);

export function useTableListAlerts(): TableListAlertsApi {
  const api = useContext(TableListAlertsContext);
  if (!api) throw new Error("useTableListAlerts must be used inside a TableList");
  return api;
}

export function TableListAlertsProvider({ api, children }: { api: TableListAlertsApi; children: ReactNode }) {
  return <TableListAlertsContext.Provider value={api}>{children}</TableListAlertsContext.Provider>;
}

/** The live region stays mounted so an alert pushed into it is announced. */
export function TableListAlertStack({
  alerts,
  onUndo,
  onDismiss,
}: {
  alerts: readonly AlertEntry[];
  onUndo: (id: string) => void;
  onDismiss: (id: string) => void;
}) {
  const { t } = useTranslation("common");
  return (
    // A plain polite live region: announces added alerts without claiming the page's single "status" role.
    <div aria-live="polite" aria-relevant="additions text">
      {alerts.length > 0 ? (
        <ul aria-label={t("tableList.alerts.label")} className="mb-3 space-y-2 pt-3">
          {alerts.map((alert) => (
            <TableListAlertItem key={alert.renderKey} alert={alert} onUndo={onUndo} onDismiss={onDismiss} />
          ))}
        </ul>
      ) : null}
    </div>
  );
}
