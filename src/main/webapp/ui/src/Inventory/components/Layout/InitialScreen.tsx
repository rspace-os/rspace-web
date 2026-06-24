import Container from "@mui/material/Container";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import useStores from "../../../stores/use-stores";
import Main from "../../Main";
import useNavigateHelpers from "../../useNavigateHelpers";
import Header from "./Header";
import Layout from "./Layout2x1";
import Sidebar from "./Sidebar";

function InitialScreen(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const { peopleStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();

  const navFilters = [
    {
      label: t("layout.sidebar.myBench"),
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
      label: t("layout.sidebar.containers"),
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
      label: t("layout.sidebar.samples"),
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
    return buttons.map(({ label, icon, onClick }) => (
      <ListItem key={label} disablePadding>
        <ListItemButton onClick={onClick}>
          <ListItemIcon>{icon}</ListItemIcon>
          <ListItemText primary={label} />
        </ListItemButton>
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
            <Container sx={{ margin: "20px 0px" }}>
              <Typography variant="subtitle1" gutterBottom>
                {t("layout.initialScreen.navigateTo")}
              </Typography>
              <List component="nav">{navButtons(navFilters)}</List>
            </Container>
          }
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          colRight={<></>}
        />
      </Main>
    </>
  );
}

export default observer(InitialScreen);
