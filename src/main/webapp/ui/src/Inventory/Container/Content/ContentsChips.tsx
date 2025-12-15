import React from "react";
import { type Container } from "../../../stores/definitions/Container";
import CountChip from "./CountChip";

type ContentsChipsArgs = {
  record: Container;
};

export default function ContentsChips({
  record,
}: ContentsChipsArgs): React.ReactNode {
  return (
    <>
      {record.canStoreContainers && (
        <CountChip type="container" record={record} />
      )}
      {record.canStoreSamples && <CountChip type="subSample" record={record} />}
    </>
  );
}
