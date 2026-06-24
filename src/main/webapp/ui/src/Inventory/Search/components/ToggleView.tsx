import AccountTreeOutlinedIcon from "@mui/icons-material/AccountTreeOutlined";
import AppsOutlinedIcon from "@mui/icons-material/AppsOutlined";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import ImageOutlinedIcon from "@mui/icons-material/ImageOutlined";
import ListOutlinedIcon from "@mui/icons-material/ListOutlined";
import Box from "@mui/material/Box";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import type { SxProps, Theme } from "@mui/material/styles";
import React from "react";
import { useTranslation } from "react-i18next";
import StyledMenu from "@/components/StyledMenu";
import type { SearchView } from "@/stores/definitions/Search";
import DropdownButton from "../../../components/DropdownButton";

const Icon = ({ type, sx }: { type: SearchView; sx?: SxProps<Theme> }) =>
  ({
    LIST: <ListOutlinedIcon sx={sx} />,
    TREE: <AccountTreeOutlinedIcon sx={sx} />,
    CARD: <DnsOutlinedIcon sx={sx} />,
    GRID: <AppsOutlinedIcon sx={sx} />,
    IMAGE: <ImageOutlinedIcon sx={sx} />,
  })[type];

type ToggleViewArgs = {
  onChange: (newView: SearchView) => Promise<void>;
  currentView: SearchView;
  views: Array<SearchView>;
};

export default function ToggleView({ onChange, currentView, views }: ToggleViewArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);

  const handleClick = (event: { currentTarget: HTMLElement }) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const setView = (viewType: SearchView) => {
    void onChange(viewType);
    handleClose();
  };

  const viewLabel = (type: SearchView): string =>
    ({
      LIST: t("search.controls.view.list"),
      TREE: t("search.controls.view.tree"),
      CARD: t("search.controls.view.card"),
      GRID: t("search.controls.view.grid"),
      IMAGE: t("search.controls.view.image"),
    })[type];

  return (
    <Box sx={{ alignSelf: "center" }}>
      <DropdownButton
        name={
          <Icon
            type={currentView}
            sx={(theme) => ({
              borderRadius: theme.spacing(1),
              color: theme.palette.standardIcon.main,
            })}
          />
        }
        onClick={handleClick}
        title={t("search.controls.view.changeView")}
      >
        <StyledMenu anchorEl={anchorEl} keepMounted open={Boolean(anchorEl)} onClose={handleClose}>
          {views.map((type, i) => (
            <MenuItem
              key={i}
              onClick={() => setView(type)}
              selected={type === currentView}
              aria-current={type === currentView}
            >
              <ListItemIcon>
                <Icon type={type} />
              </ListItemIcon>
              <ListItemText primary={viewLabel(type)} />
            </MenuItem>
          ))}
        </StyledMenu>
      </DropdownButton>
    </Box>
  );
}
