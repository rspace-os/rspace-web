import Container from "@mui/material/Container";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import useStores from "../../../stores/use-stores";
import Main from "../../Main";
import useNavigateHelpers from "../../useNavigateHelpers";
import Header from "./Header";
import Layout from "./Layout2x1";
import Sidebar from "./Sidebar";

function InitialScreen(): React.ReactNode {
    const theme = useTheme();
    const { peopleStore } = useStores();
    const { navigateToSearch } = useNavigateHelpers();

    const navFilters = [
        {
            label: "My Bench",
            onClick: (e: React.MouseEvent<HTMLElement>) => {
                e.stopPropagation();
                navigateToSearch(
                    peopleStore.currentUser
                        ? {
                              parentGlobalId: `BE${peopleStore.currentUser.workbenchId}`,
                          }
                        : {},
                );
            },
            icon: <MyBenchIcon />,
        },
        {
            label: "Containers",
            onClick: (e: React.MouseEvent<HTMLElement>) => {
                e.stopPropagation();
                navigateToSearch({ resultType: "CONTAINER" });
            },
            icon: (
                <RecordTypeIcon
                    record={{
                        iconName: "container",
                        recordTypeLabel: "",
                    }}
                    style={{ width: "24px" }}
                    color={theme.palette.standardIcon.main}
                />
            ),
        },
        {
            label: "Samples",
            onClick: (e: React.MouseEvent<HTMLElement>) => {
                e.stopPropagation();
                navigateToSearch({ resultType: "SAMPLE" });
            },
            icon: (
                <RecordTypeIcon
                    record={{
                        iconName: "sample",
                        recordTypeLabel: "",
                    }}
                    style={{ width: "24px" }}
                    color={theme.palette.standardIcon.main}
                />
            ),
        },
    ];

    const navButtons = (
        buttons: Array<{
            label: string;
            icon: React.ReactNode;
            onClick: (e: React.MouseEvent<HTMLElement>) => void;
        }>,
    ) => {
        return buttons.map((args) => (
            <ListItem key={args.label} button {...args}>
                <ListItemIcon>{args.icon}</ListItemIcon>
                <ListItemText primary={args.label} />
            </ListItem>
        ));
    };

    const sidebarId = React.useId();

    return (
        <>
            <Header sidebarId={sidebarId} />
            <Sidebar id={sidebarId} />
            <Main>
                <Layout
                    colLeft={
                        <Container style={{ margin: "20px 0px" }}>
                            <Typography variant="subtitle1" gutterBottom>
                                Navigate to:
                            </Typography>
                            <List component="nav">{navButtons(navFilters)}</List>
                        </Container>
                    }
                    colRight={<></>}
                />
            </Main>
        </>
    );
}

export default observer(InitialScreen);
