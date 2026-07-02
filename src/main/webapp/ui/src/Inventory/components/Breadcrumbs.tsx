import MuiBreadcrumbs from "@mui/material/Breadcrumbs";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { hasLocation } from "../../stores/models/HasLocation";
import { CurrentRecord, InTrash, RecordLink, TopLink } from "./RecordLink";

type BreadcrumbsArgs = {
  record: InventoryRecord;
  showCurrent?: boolean;
};

const _Breadcrumbs = ({ record, showCurrent = false }: BreadcrumbsArgs): React.ReactNode => {
  const { t } = useTranslation("inventory");
  const showTopLink = record.showTopLinkInBreadcrumbs();

  const parents = hasLocation(record)
    .map((r) => r.allParentContainers.toReversed())
    .orElse([]);

  return (
    <MuiBreadcrumbs aria-label={t("breadcrumbs.label")}>
      {showTopLink && <TopLink />}
      {parents.map((parent) => (
        <RecordLink key={parent.id} record={parent} overflow />
      ))}
      {showCurrent && <CurrentRecord record={record} overflow />}
      {record.deleted && <InTrash />}
    </MuiBreadcrumbs>
  );
};

const Breadcrumbs = observer(_Breadcrumbs);
export default Breadcrumbs;
