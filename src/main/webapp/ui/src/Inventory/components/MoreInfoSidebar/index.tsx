import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import { observer } from "mobx-react-lite";
import type React from "react";
import { makeStyles } from "tss-react/mui";
import type { Factory } from "../../../stores/definitions/Factory";
import useStores from "../../../stores/use-stores";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import Date from "./Date";
import GlobalId from "./GlobalId";
import LatestTemplateActions from "./LatestTemplateActions";
import LinkedDocuments from "./LinkedDocuments";
import TemplateVersion from "./TemplateVersion";

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
                    <IconButton onClick={() => uiStore.toggleInfo()} aria-label="Close more info sidebar">
                        <ChevronRightIcon />
                    </IconButton>
                </CardActions>
                <Divider />
                <CardContent className={classes.cardContent}>
                    <Grid container direction="column" spacing={5}>
                        <GlobalId record={activeResult} />
                        <Date label="Created" date={activeResult.created} />
                        <Date label="Last Modified" date={activeResult.lastModified} />
                        <TemplateVersion record={activeResult} />
                        <LatestTemplateActions record={activeResult} />
                        {activeResult.usableInLoM && activeResult.globalId && (
                            <LinkedDocuments globalId={activeResult.globalId} factory={factory} />
                        )}
                    </Grid>
                </CardContent>
            </Card>
        </Drawer>
    );
}

export default observer(Sidebar);
