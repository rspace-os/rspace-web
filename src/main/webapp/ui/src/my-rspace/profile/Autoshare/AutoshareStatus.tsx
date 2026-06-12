// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import DisableAutoshareDialog from "./DisableAutoshareDialog";
import EnableAutoshareDialog from "./EnableAutoshareDialog";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function AutoshareStatus(props: any) {
  return (
    <>
      {!props.group.labGroup && <em>Autosharing is only possible for LabGroups</em>}
      {props.isCurrentlySharing && props.group.labGroup && <em>Autosharing is in progress</em>}
      {!props.isCurrentlySharing && props.group.autoshareEnabled && props.group.labGroup && (
        <DisableAutoshareDialog {...props} />
      )}
      {!props.isCurrentlySharing && !props.group.autoshareEnabled && props.group.labGroup && (
        <EnableAutoshareDialog {...props} />
      )}
    </>
  );
}
