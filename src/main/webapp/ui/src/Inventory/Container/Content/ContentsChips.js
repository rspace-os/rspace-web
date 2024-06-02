//@flow

import React, { type Node } from "react";
import { type Container } from "../../../stores/definitions/Container";
import CountChip from "./CountChip";

type ContentsChipsArgs = {|
  record: Container,
|};

export default function ContentsChips({ record }: ContentsChipsArgs): Node {
  return (
    <>
      {record.canStoreContainers && (
        <CountChip type="container" record={record} />
      )}
      {record.canStoreSamples && <CountChip type="subSample" record={record} />}
    </>
  );
}
