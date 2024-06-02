//@flow

import React, { type Node, type ComponentType } from "react";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { observer } from "mobx-react-lite";
import Typography from "@mui/material/Typography";
import Container from "@mui/material/Container";
import Layout from "./Layout2x1";
import useNavigateHelpers from "../../useNavigateHelpers";
import useStores from "../../../stores/use-stores";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import Header from "./Header";
import Sidebar from "./Sidebar";
import Main from "../../Main";
import { useTheme } from "@mui/material/styles";
import RecordTypeIcon from "../../../components/RecordTypeIcon";

function InitialScreen(): Node {
  const theme = useTheme();
  const { peopleStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();

  const navFilters = [
    {
      label: "My Bench",
      onClick: (e: Event) => {
        e.stopPropagation();
        navigateToSearch(
          peopleStore.currentUser
            ? {
                parentGlobalId: `BE${peopleStore.currentUser.workbenchId}`,
              }
            : {}
        );
      },
      icon: <MyBenchIcon />,
    },
    {
      label: "Containers",
      onClick: (e: Event) => {
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
          color={theme.palette.standardIcon}
        />
      ),
    },
    {
      label: "Samples",
      onClick: (e: Event) => {
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
          color={theme.palette.standardIcon}
        />
      ),
    },
  ];

  const navButtons = (
    buttons: Array<{| label: string, icon: Node, onClick: (Event) => void |}>
  ) => {
    return buttons.map((args) => (
      <ListItem key={args.label} button {...args}>
        <ListItemIcon>{args.icon}</ListItemIcon>
        <ListItemText primary={args.label} />
      </ListItem>
    ));
  };

  return (
    <>
      <Header />
      <Sidebar />
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

export default (observer(InitialScreen): ComponentType<{||}>);
