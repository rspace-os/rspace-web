//@flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import {
  fetchIntegrationInfo,
  type IntegrationInfo,
} from "../../common/integrationHelpers";
import DMPToolIcon from "../../eln/apps/icons/dmptool.svg";
import { COLOR } from "../../eln/apps/integrations/DMPTool";
import CardMedia from "@mui/material/CardMedia";
import axios from "axios";
import { mapNullable } from "../../util/Util";
import { type UseState } from "../../util/types";

export default function DMPToolNewMenuItem(): Node {
  const [
    dmpToolIntegrationInfo,
    setDmpToolIntegrationInfo,
  ]: UseState<?IntegrationInfo> = React.useState(null);
  const [DMPHost, setDMPHost] = React.useState<?string>();
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  React.useEffect(() => {
    axios
      .get<void, string>("/apps/dmptool/baseUrlHost")
      .then((r) => setDMPHost(r.data))
      .catch((e) => console.error("Cannot establish DMP host", e));
  }, []);

  React.useEffect(() => {
    fetchIntegrationInfo("DMPTOOL")
      .then((r) => setDmpToolIntegrationInfo(r))
      .catch((e) =>
        console.error("Cannot establish if DMPTool app is enabled", e)
      );
  }, []);

  if (!dmpToolIntegrationInfo || !dmpToolIntegrationInfo.enabled) return null;
  return (
    <>
      <NewMenuItem
        title="DMPTool"
        avatar={<CardMedia image={DMPToolIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 30 }}
        subheader={`Import from ${
          mapNullable((host) => ` ${host}`, DMPHost) ?? "an unknown domain."
        }`}
        onClick={() => {
          setShowDMPDialog(true);
        }}
      />
      <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
    </>
  );
}
