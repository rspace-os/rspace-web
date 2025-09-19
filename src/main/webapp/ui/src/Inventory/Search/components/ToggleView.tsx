import React from "react";
import AccountTreeOutlinedIcon from "@mui/icons-material/AccountTreeOutlined";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import ListOutlinedIcon from "@mui/icons-material/ListOutlined";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import { makeStyles } from "tss-react/mui";
import AppsOutlinedIcon from "@mui/icons-material/AppsOutlined";
import ImageOutlinedIcon from "@mui/icons-material/ImageOutlined";
import DropdownButton from "../../../components/DropdownButton";
import {
  type SearchView,
  TYPE_LABEL,
} from "../../../stores/definitions/Search";

const Icon = ({ type, className }: { type: SearchView; className?: string }) =>
  ({
    LIST: <ListOutlinedIcon className={className} />,
    TREE: <AccountTreeOutlinedIcon className={className} />,
    CARD: <DnsOutlinedIcon className={className} />,
    GRID: <AppsOutlinedIcon className={className} />,
    IMAGE: <ImageOutlinedIcon className={className} />,
  })[type];

const useStyles = makeStyles()((theme) => ({
  container: {
    alignSelf: "center",
  },
  icon: {
    borderRadius: theme.spacing(1),
    color: theme.palette.standardIcon.main,
  },
}));

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
  const { classes } = useStyles();

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
    <div className={classes.container}>
      <DropdownButton
        name={<Icon type={currentView} className={classes.icon} />}
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
            <StyledMenuItem
              key={i}
              onClick={() => setView(type)}
              selected={type === currentView}
              aria-current={type === currentView}
            >
              <ListItemIcon>
                <Icon type={type} />
              </ListItemIcon>
              <ListItemText primary={TYPE_LABEL[type]} />
            </StyledMenuItem>
          ))}
        </StyledMenu>
      </DropdownButton>
    </div>
  );
}
