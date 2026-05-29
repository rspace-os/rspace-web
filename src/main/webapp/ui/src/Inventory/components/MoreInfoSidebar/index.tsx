import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import { makeStyles } from "tss-react/mui";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { observer } from "mobx-react-lite";
import React from "react";
import useStores from "../../../stores/use-stores";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import SidebarBody from "./SidebarBody";
import { type Factory } from "../../../stores/definitions/Factory";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

const useStyles = makeStyles()(() => ({
  card: {
    display: "flex",
    flexDirection: "column",
  },
  cardContent: {
    overflowX: "hidden",
  },
}));

type SidebarArgs = {
  factory: Factory | null;
};

function Sidebar({ factory }: SidebarArgs): React.ReactNode {
  const {
    uiStore,
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const persistant = !isSingleColumnLayout;
  const { classes } = useStyles();

  const closeSidebar = () => {
    uiStore.toggleInfo(false);
  };

  if (!activeResult) return null;
  return (
    <Drawer
      variant={persistant ? "persistent" : "temporary"}
      anchor="right"
      open={uiStore.infoVisible && Boolean(activeResult)}
      onClose={closeSidebar}
    >
      <Card elevation={0} className={classes.card}>
        <CardActions>
          <IconButton
            onClick={() => uiStore.toggleInfo()}
            aria-label="Close more info sidebar"
          >
            <ChevronRightIcon />
          </IconButton>
        </CardActions>
        <Divider />
        <CardContent className={classes.cardContent}>
          <SidebarBody record={activeResult} factory={factory} />
        </CardContent>
      </Card>
    </Drawer>
  );
}

export default observer(Sidebar);
