"use strict";
import React, { useEffect, useRef } from "react";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import SymbolsMenu from "./symbolsMenu";
import { humanize } from "../../util/shortcuts";

export default function SymbolsTab(props) {
  const shortcutsEndRef = useRef(null);
  const [isFirst, setIsFirst] = React.useState(true);

  const label = (code) => {
    const reducer = (accumulator, currentValue) =>
      accumulator.concat(props.config.symbols[currentValue]);
    const all_symbols = Object.keys(props.config.symbols).reduce(reducer, []);
    return all_symbols.find((s) => s[0] == code)[1];
  };

  const scrollToBottom = () => {
    if (isFirst) {
      setIsFirst(false);
    } else {
      shortcutsEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  };

  useEffect(scrollToBottom, [props.symbolShortcuts]);

  return (
    <>
      <SymbolsMenu
        symbols={props.config.symbols}
        onNewShortcut={props.onNewShortcut}
      />
      {Object.keys(props.symbolShortcuts).map((key) => (
        <Grid container key={key}>
          <Grid item xs={6}>
            <Typography
              variant="overline"
              display="block"
              style={{ marginTop: "8px" }}
            >
              <b style={{ marginRight: "10px" }}>{String.fromCharCode(key)}</b>
              {label(key)}
            </Typography>
          </Grid>
          <Grid item xs={6}>
            <TextField
              variant="standard"
              fullWidth
              value={humanize(props.symbolShortcuts[key]) || ""}
              error={props.hasError && key == props.selectedKey}
              helperText={
                key == props.selectedKey && props.hasError
                  ? props.errorMessage
                  : null
              }
              onKeyDown={(e) => props.detectShortcut(key, e)}
              onKeyUp={props.onKeyUp}
              margin="dense"
              inputProps={{ style: { lineHeight: "20px" } }}
            />
          </Grid>
        </Grid>
      ))}
      <div ref={shortcutsEndRef} />
    </>
  );
}
