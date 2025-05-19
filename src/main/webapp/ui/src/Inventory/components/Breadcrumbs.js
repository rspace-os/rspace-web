//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import MuiBreadcrumbs from "@mui/material/Breadcrumbs";
import { RecordLink, TopLink, CurrentRecord, InTrash } from "./RecordLink";
import * as ArrayUtils from "../../util/ArrayUtils";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { hasLocation } from "../../stores/models/HasLocation";

type BreadcrumbsArgs = {|
  record: InventoryRecord,
  showCurrent?: boolean,
|};

const _Breadcrumbs = ({
  record,
  showCurrent = false,
}: BreadcrumbsArgs): Node => {
  const showTopLink = record.showTopLinkInBreadcrumbs();

  const parents = hasLocation(record)
    .map((r) => ArrayUtils.reverse(r.allParentContainers))
    .orElse(([]: Array<InventoryRecord>));

  return (
    <MuiBreadcrumbs aria-label="breadcrumb">
      {showTopLink && <TopLink />}
      {parents.map((parent) => (
        <RecordLink key={parent.id} record={parent} overflow />
      ))}
      {showCurrent && <CurrentRecord record={record} overflow />}
      {record.deleted && <InTrash />}
    </MuiBreadcrumbs>
  );
};

const Breadcrumbs: ComponentType<BreadcrumbsArgs> = observer(_Breadcrumbs);
export default Breadcrumbs;
