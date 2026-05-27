"use strict";
import React from "react";
import DisableAutoshareDialog from "./DisableAutoshareDialog";
import EnableAutoshareDialog from "./EnableAutoshareDialog";

export default function AutoshareStatus(props) {
  return (
    <>
      {!props.group.labGroup && (
        <i>Autosharing is only possible for LabGroups </i>
      )}
      {props.isCurrentlySharing && props.group.labGroup && (
        <i>Autosharing is in progress</i>
      )}
      {!props.isCurrentlySharing &&
        props.group.autoshareEnabled &&
        props.group.labGroup && <DisableAutoshareDialog {...props} />}
      {!props.isCurrentlySharing &&
        !props.group.autoshareEnabled &&
        props.group.labGroup && <EnableAutoshareDialog {...props} />}
    </>
  );
}
