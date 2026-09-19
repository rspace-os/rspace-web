import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { RequestsFilter, StatusFilter } from "./RequestsList";

function ParameterChip({ label }: { label: string }): React.ReactNode {
  return <Chip size="small" label={label} />;
}

/**
 * Shows the currently active "Requests"/"Status" search options as chips, in
 * the same style as the main Inventory search's parameter chips. These chips
 * are not deletable; use the dropdowns above to change the filters.
 */
export default function RequestsParameterChips({
  requestsFilter,
  statusFilter,
}: {
  requestsFilter: RequestsFilter;
  statusFilter: StatusFilter;
}): React.ReactNode {
  const { t } = useTranslation("inventory");

  const requestsFilterLabel =
    requestsFilter === "all"
      ? t("requestsManagement.filters.requests.all")
      : requestsFilter === "sent"
        ? t("requestsManagement.filters.requests.sent")
        : t("requestsManagement.filters.requests.received");
  const statusLabel =
    statusFilter === "all"
      ? t("requestsManagement.filters.status.all")
      : statusFilter === "active"
        ? t("requestsManagement.filters.status.active")
        : t("requestsManagement.filters.status.past");

  return (
    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
      <ParameterChip label={t("requestsManagement.chips.requests", { value: requestsFilterLabel })} />
      <ParameterChip label={t("requestsManagement.chips.status", { value: statusLabel })} />
    </Stack>
  );
}
