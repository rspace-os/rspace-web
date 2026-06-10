"use strict";
import React from "react";
import Grid from "@mui/material/Grid";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Button from "@mui/material/Button";
import Tooltip from "@mui/material/Tooltip";
import Paper from "@mui/material/Paper";

function a11yProps(index) {
  return {
    id: `simple-tab-${index}`,
    "aria-controls": `simple-tabpanel-${index}`,
  };
}

export default function SymbolsMenu(props) {
  const [tab, setTab] = React.useState(0);

  const switchTab = (e, value) => {
    setTab(value);
  };

  const sections = () => {
    return Object.keys(props.symbols);
  };

  const selectedSymbolGroup = () => {
    return sections()[tab];
  };

  const symbols = () => {
    return props.symbols[selectedSymbolGroup()];
  };

  return (
    <Paper
      sx={{
        border: "1px solid rgb(240,240,240)",
        margin: "0px 0px 15px 0px",
        width: "100%",
      }}
      elevation={0}
    >
      <Grid container spacing={2}>
        <Grid size={3}>
          <Tabs
            orientation="vertical"
            variant="scrollable"
            value={tab}
            onChange={switchTab}
          >
            {sections().map((section) => (
              <Tab label={section} {...a11yProps(section)} key={section} />
            ))}
          </Tabs>
        </Grid>
        <Grid size={9}>
          {symbols().map((symbol) => (
            <Tooltip title={symbol[1]} aria-label={symbol[1]} key={symbol[0]}>
              <Button
                onClick={() => props.onNewShortcut(symbol)}
                sx={{ fontSize: "18px", minWidth: "45px" }}
              >
                {String.fromCharCode(symbol[0])}
              </Button>
            </Tooltip>
          ))}
        </Grid>
      </Grid>
    </Paper>
  );
}
