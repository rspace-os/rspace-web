import React from "react";
import AccountTreeOutlinedIcon from "@mui/icons-material/AccountTreeOutlined";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import Box from "@mui/material/Box";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import ListOutlinedIcon from "@mui/icons-material/ListOutlined";
import StyledMenu from "@/components/StyledMenu";
import MenuItem from "@mui/material/MenuItem";
import AppsOutlinedIcon from "@mui/icons-material/AppsOutlined";
import ImageOutlinedIcon from "@mui/icons-material/ImageOutlined";
import DropdownButton from "../../../components/DropdownButton";
import type { SxProps, Theme } from "@mui/material/styles";
import {
  type SearchView,
  TYPE_LABEL,
} from "@/stores/definitions/Search";

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

export default function ToggleView({
  onChange,
  currentView,
  views,
}: ToggleViewArgs): React.ReactNode {
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
        title="Change view"
      >
        <StyledMenu
          anchorEl={anchorEl}
          keepMounted
          open={Boolean(anchorEl)}
          onClose={handleClose}
        >
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
              <ListItemText primary={TYPE_LABEL[type]} />
            </MenuItem>
          ))}
        </StyledMenu>
      </DropdownButton>
    </Box>
  );
}
