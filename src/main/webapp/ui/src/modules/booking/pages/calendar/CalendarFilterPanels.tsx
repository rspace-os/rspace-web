import { useTranslation } from "react-i18next";
import { RestoredViewIssue } from "@/modules/common/table-list/components/RestoredViewIssue";

export type CalendarFilterPanelKind = "items" | "events";

export function CalendarFilterIssue({
  kind,
  encoded,
  network,
  onRetry,
  onReset,
}: {
  kind: CalendarFilterPanelKind;
  encoded: string;
  network: boolean;
  onRetry?: () => void;
  onReset: () => void;
}) {
  const { t: bookingT } = useTranslation("booking");
  const title = kind === "items" ? bookingT("calendar.filterGroups.items") : bookingT("calendar.filterGroups.events");
  return (
    <RestoredViewIssue
      className="mt-2 rounded-sm border border-destructive/30 bg-destructive/5 p-3"
      label={title}
      issue={{
        kind: network ? "network" : "invalid",
        encoded,
        retry: onRetry,
        remove: onReset,
      }}
    />
  );
}

export type CalendarFilterIssueState = {
  kind: CalendarFilterPanelKind;
  encoded: string;
  network: boolean;
  onRetry?: () => void;
  onReset: () => void;
};
