import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Tooltip from "@mui/material/Tooltip";
import React from "react";

function a11yProps(index: string | number) {
  return {
    id: `simple-tab-${index}`,
    "aria-controls": `simple-tabpanel-${index}`,
  };
}

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function SymbolsMenu(props: any) {
  const [tab, setTab] = React.useState(0);

  // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const switchTab = (e: any, value: number) => {
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
          <Tabs orientation="vertical" variant="scrollable" value={tab} onChange={switchTab}>
            {sections().map((section) => (
              <Tab label={section} {...a11yProps(section)} key={section} />
            ))}
          </Tabs>
        </Grid>
        <Grid size={9}>
          {/* biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion */}
          {symbols().map((symbol: any) => (
            <Tooltip title={symbol[1]} aria-label={symbol[1]} key={symbol[0]}>
              <Button onClick={() => props.onNewShortcut(symbol)} sx={{ fontSize: "18px", minWidth: "45px" }}>
                {String.fromCharCode(symbol[0])}
              </Button>
            </Tooltip>
          ))}
        </Grid>
      </Grid>
    </Paper>
  );
}
