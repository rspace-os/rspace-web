//@flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import DMPToolIcon from "../../eln/apps/icons/dmptool.svg";
import { COLOR } from "../../eln/apps/integrations/DMPTool";
import CardMedia from "@mui/material/CardMedia";
import axios from "axios";
import { mapNullable } from "../../util/Util";
import EventBoundary from "../../components/EventBoundary";

type DMPToolNewMenuItemArgs = {|
  onDialogClose: () => void,
|};

export default function DMPToolNewMenuItem({
  onDialogClose,
}: DMPToolNewMenuItemArgs): Node {
  const [DMPHost, setDMPHost] = React.useState<?string>();
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  React.useEffect(() => {
    axios
      .get<void, string>("/apps/dmptool/baseUrlHost")
      .then((r) => setDMPHost(r.data))
      .catch((e) => console.error("Cannot establish DMP host", e));
  }, []);

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
        aria-haspopup="dialog"
      />
      <EventBoundary>
        <DMPDialog
          open={showDMPDialog}
          setOpen={(b) => {
            setShowDMPDialog(b);
            onDialogClose();
          }}
        />
      </EventBoundary>
    </>
  );
}
