import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { observer } from "mobx-react-lite";
import React from "react";
import useStores from "../../../stores/use-stores";
import GlobalId from "./GlobalId";
import Date from "./Date";
import LatestTemplateActions from "./LatestTemplateActions";
import TemplateVersion from "./TemplateVersion";
import Grid from "@mui/material/Grid";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import LinkedDocuments from "./LinkedDocuments";
import type { Factory } from "../../../stores/definitions/Factory";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

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
      <Card elevation={0} sx={{ display: "flex", flexDirection: "column" }}>
        <CardActions>
          <IconButton
            onClick={() => uiStore.toggleInfo()}
            aria-label="Close more info sidebar"
          >
            <ChevronRightIcon />
          </IconButton>
        </CardActions>
        <Divider />
        <CardContent sx={{ overflowX: "hidden" }}>
          <Grid container sx={{ flexDirection: "column" }} spacing={5}>
            <GlobalId record={activeResult} />
            <Date label="Created" date={activeResult.created} />
            <Date label="Last Modified" date={activeResult.lastModified} />
            <TemplateVersion record={activeResult} />
            <LatestTemplateActions record={activeResult} />
            {activeResult.usableInLoM && activeResult.globalId && (
              <LinkedDocuments
                globalId={activeResult.globalId}
                factory={factory}
              />
            )}
          </Grid>
        </CardContent>
      </Card>
    </Drawer>
  );
}

export default observer(Sidebar);
