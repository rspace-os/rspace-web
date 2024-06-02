//@flow

import React, { type Node } from "react";
import { RecordLink, TopLink, InTrash } from "./RecordLink";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";

type RecordLocationArgs = {|
  record: InventoryRecord,
|};

export default function RecordLocation({ record }: RecordLocationArgs): Node {
  if (record.hasParentContainers())
    // $FlowExpectedError[prop-missing] hasParentContainers assures immediateParentContainer
    return <RecordLink record={record.immediateParentContainer} overflow />;
  if (record.deleted) return <InTrash />;
  if (record.showTopLinkInBreadcrumbs()) return <TopLink />;
  return <>&mdash;</>;
}
