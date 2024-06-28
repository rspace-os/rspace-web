//@flow

import React, { type Node } from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import Box from "@mui/material/Box";
import useViewportDimensions from "../../../util/useViewportDimensions";
import Typography from "@mui/material/Typography";
import MenuIcon from "@mui/icons-material/Menu";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchIcon from "@mui/icons-material/Search";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import { styled } from "@mui/material/styles";
import CloseIcon from "@mui/icons-material/Close";
import AccessibilityTips from "../../../components/AccessibilityTips";
import HelpDocs from "../../../components/Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";

const StyledCloseIcon = styled(CloseIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

export default function GalleryAppBar({
  appliedSearchTerm,
  setAppliedSearchTerm,
  setDrawerOpen,
  drawerOpen,
}: {|
  appliedSearchTerm: string,
  setAppliedSearchTerm: (string) => void,
  setDrawerOpen: (boolean) => void,
  drawerOpen: boolean,
|}): Node {
  const viewport = useViewportDimensions();
  const [showTextfield, setShowTextfield] = React.useState(false);
  const searchTextfield = React.useRef();
  const [searchTerm, setSearchTerm] = React.useState("");

  return (
    <AppBar position="relative" open={true}>
      <Toolbar variant="dense">
        <IconButton
          aria-label={drawerOpen ? "close drawer" : "open drawer"}
          onClick={() => {
            setDrawerOpen(!drawerOpen);
          }}
        >
          <MenuIcon />
        </IconButton>
        {(!viewport.isViewportVerySmall || !showTextfield) && (
          <Typography variant="h6" noWrap component="h2">
            Gallery
          </Typography>
        )}
        <Box
          flexGrow={viewport.isViewportVerySmall && showTextfield ? 0 : 1}
        ></Box>
        {viewport.isViewportVerySmall && !showTextfield && (
          <IconButtonWithTooltip
            size="small"
            onClick={() => {
              setShowTextfield(true);
              setTimeout(() => {
                searchTextfield.current?.focus();
              }, 0);
            }}
            icon={<SearchIcon />}
            title="Search this folder"
          />
        )}
        <Box
          mx={1}
          sx={{
            flexGrow: viewport.isViewportSmall ? 1 : 0,
            display:
              !viewport.isViewportVerySmall || showTextfield ? "block" : "none",
          }}
        >
          <form
            onSubmit={(event) => {
              event.preventDefault();
              setAppliedSearchTerm(searchTerm);
              setShowTextfield(searchTerm !== "");
            }}
          >
            <TextField
              placeholder="Search"
              sx={{
                /*
                 * This is so that it doesn't obscure the "Gallery" heading on
                 * very small mobile viewports. 300px is arbitrary width that
                 * does is smaller enough to not cause any overlapping issues.
                 */
                maxWidth: viewport.isViewportSmall ? "100%" : 300,
                width: viewport.isViewportSmall ? "100%" : 300,
              }}
              value={searchTerm}
              onChange={({ currentTarget: { value } }) => setSearchTerm(value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
                endAdornment:
                  searchTerm !== "" ? (
                    <IconButtonWithTooltip
                      title="Clear"
                      icon={<StyledCloseIcon />}
                      size="small"
                      onClick={() => {
                        setSearchTerm("");
                        setAppliedSearchTerm("");
                        setShowTextfield(false);
                      }}
                    />
                  ) : null,
              }}
              inputProps={{
                ref: searchTextfield,
                onBlur: () => {
                  setSearchTerm(appliedSearchTerm);
                  setShowTextfield(appliedSearchTerm !== "");
                },
              }}
            />
          </form>
        </Box>
        <Box ml={1}>
          <AccessibilityTips
            elementType="dialog"
            supportsReducedMotion
            supportsHighContrastMode
          />
        </Box>
        <Box ml={1}>
          <HelpDocs
            Action={({ onClick, disabled }) => (
              <IconButtonWithTooltip
                size="small"
                onClick={onClick}
                icon={<HelpIcon />}
                title="Open Help"
                disabled={disabled}
              />
            )}
          />
        </Box>
      </Toolbar>
    </AppBar>
  );
}
