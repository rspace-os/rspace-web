import React from "react";
import { RecordLink, TopLink, InTrash } from "./RecordLink";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { hasLocation } from "../../stores/models/HasLocation";
import { Optional } from "../../util/optional";
import { HasLocation } from "../../stores/definitions/HasLocation";

type RecordLocationArgs = {
  record: InventoryRecord;
};

export default function RecordLocation({
  record,
}: RecordLocationArgs): React.ReactNode {
  return hasLocation(record)
    .flatMap((r: HasLocation & InventoryRecord) => {
      if (!r.immediateParentContainer)
        return Optional.empty<React.ReactElement<typeof RecordLink>>();
      return Optional.present(
        <RecordLink
          key={r.globalId}
          record={r.immediateParentContainer}
          overflow
        />
      );
    })
    .orElseGet(() => {
      if (record.deleted) return <InTrash />;
      if (record.showTopLinkInBreadcrumbs()) return <TopLink />;
      return <>&mdash;</>;
    });
}
