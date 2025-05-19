//@flow

import React, { type Node } from "react";
import { RecordLink, TopLink, InTrash } from "./RecordLink";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { hasLocation } from "../../stores/models/HasLocation";
import { Optional } from "../../util/optional";

type RecordLocationArgs = {|
  record: InventoryRecord,
|};

export default function RecordLocation({ record }: RecordLocationArgs): Node {
  return hasLocation(record)
    .flatMap((r) => {
      if (!r.immediateParentContainer)
        return Optional.empty<React.Element<typeof RecordLink>>();
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
