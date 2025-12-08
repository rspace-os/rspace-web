import MuiBreadcrumbs from "@mui/material/Breadcrumbs";
import { observer } from "mobx-react-lite";
import type React from "react";
import type { InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { hasLocation } from "../../stores/models/HasLocation";
import * as ArrayUtils from "../../util/ArrayUtils";
import { CurrentRecord, InTrash, RecordLink, TopLink } from "./RecordLink";

type BreadcrumbsArgs = {
    record: InventoryRecord;
    showCurrent?: boolean;
};

const _Breadcrumbs = ({ record, showCurrent = false }: BreadcrumbsArgs): React.ReactNode => {
    const showTopLink = record.showTopLinkInBreadcrumbs();

    const parents = hasLocation(record)
        .map((r) => ArrayUtils.reverse(r.allParentContainers))
        .orElse([]);

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

const Breadcrumbs = observer(_Breadcrumbs);
export default Breadcrumbs;
