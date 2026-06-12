import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React, { useEffect, useRef } from "react";
import { humanize } from "../../util/shortcuts";
import SymbolsMenu from "./SymbolsMenu";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function SymbolsTab(props: any) {
  const shortcutsEndRef = useRef<HTMLDivElement>(null);
  const [isFirst, setIsFirst] = React.useState(true);

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const label = (code: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    const reducer = (accumulator: any[], currentValue: string) =>
      accumulator.concat(props.config.symbols[currentValue]);
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    const all_symbols: any[] = Object.keys(props.config.symbols).reduce(reducer, []);
    // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
    return all_symbols.find((s) => s[0] == code)[1];
  };

  const scrollToBottom = () => {
    if (isFirst) {
      setIsFirst(false);
    } else {
      shortcutsEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  };

  useEffect(scrollToBottom, [props.symbolShortcuts]);

  return (
    <>
      <SymbolsMenu symbols={props.config.symbols} onNewShortcut={props.onNewShortcut} />
      {Object.keys(props.symbolShortcuts).map((key) => (
        <Grid container key={key}>
          <Grid size={6}>
            <Typography variant="overline" sx={{ display: "block", marginTop: "8px" }}>
              <Typography variant="inherit" component="strong" sx={{ marginRight: "10px" }}>
                {String.fromCharCode(key as unknown as number)}
              </Typography>
              {label(key)}
            </Typography>
          </Grid>
          <Grid size={6}>
            <TextField
              variant="standard"
              fullWidth
              value={humanize(props.symbolShortcuts[key]) || ""}
              // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
              error={props.hasError && key == props.selectedKey}
              helperText={
                // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
                key == props.selectedKey && props.hasError ? props.errorMessage : null
              }
              onKeyDown={(e) => props.detectShortcut(key, e)}
              onKeyUp={props.onKeyUp}
              margin="dense"
              slotProps={{
                htmlInput: { style: { lineHeight: "20px" } },
              }}
            />
          </Grid>
        </Grid>
      ))}
      <div ref={shortcutsEndRef} />
    </>
  );
}
