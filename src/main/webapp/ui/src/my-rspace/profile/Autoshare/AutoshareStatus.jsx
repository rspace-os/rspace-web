"use strict";
import React from "react";
import DisableAutoshareDialog from "./DisableAutoshareDialog";
import EnableAutoshareDialog from "./EnableAutoshareDialog";

export default function AutoshareStatus(props) {
  return (
    <>
      {!props.group.labGroup && (
        <em>Autosharing is only possible for LabGroups </em>
      )}
      {props.isCurrentlySharing && props.group.labGroup && (
        <em>Autosharing is in progress</em>
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
