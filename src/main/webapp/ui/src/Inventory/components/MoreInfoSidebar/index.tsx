import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { Factory } from "../../../stores/definitions/Factory";
import useStores from "../../../stores/use-stores";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import SidebarBody from "./SidebarBody";

type SidebarArgs = {
  factory: Factory | null;
};

function Sidebar({ factory }: SidebarArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
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
          <IconButton onClick={() => uiStore.toggleInfo()} aria-label={t("moreInfo.closeSidebar")}>
            <ChevronRightIcon />
          </IconButton>
        </CardActions>
        <Divider />
        <CardContent sx={{ overflowX: "hidden" }}>
          <SidebarBody record={activeResult} factory={factory} />
        </CardContent>
      </Card>
    </Drawer>
  );
}

export default observer(Sidebar);
