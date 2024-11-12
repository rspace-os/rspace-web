//@flow

import React, { type Node } from "react";
import { ThemeProvider } from "@mui/material/styles";
import Box from "@mui/material/Box";
import Dialog from "@mui/material/Dialog";
import createAccentedTheme from "../../accentedTheme";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../components/AccessibilityTips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import InvApiService from "../../common/InvApiService";
import { doNotAwait } from "../../util/Util";

export const FIELDMARK_COLOR = {
  main: {
    hue: 82,
    saturation: 80,
    lightness: 33,
  },
  darker: {
    hue: 82,
    saturation: 80,
    lightness: 22,
  },
  contrastText: {
    hue: 82,
    saturation: 80,
    lightness: 19,
  },
  background: {
    hue: 82,
    saturation: 46,
    lightness: 66,
  },
  backgroundContrastText: {
    hue: 82,
    saturation: 70,
    lightness: 22,
  },
};

type FieldmarkImportDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

type Notebook = { ... };

export default function FieldmarkImportDialog({
  open,
  onClose,
}: FieldmarkImportDialogArgs): Node {
  const [notebooks, setNotebooks] =
    React.useState<null | $ReadOnlyArray<Notebook>>(null);

  React.useEffect(
    doNotAwait(async () => {
      try {
        const { data } = await InvApiService.get<
          mixed,
          $ReadOnlyArray<Notebook>
        >("/fieldmark/notebooks");
        setNotebooks(data);
      } catch (e) {
        console.error(e);
      }
    }),
    []
  );

  return (
    <ThemeProvider theme={createAccentedTheme(FIELDMARK_COLOR)}>
      <Dialog open={open} onClose={onClose}>
        <AppBar position="relative" open={true}>
          <Toolbar variant="dense">
            <Typography variant="h6" noWrap component="h2">
              Fieldmark
            </Typography>
            <Box flexGrow={1}></Box>
            <Box ml={1}>
              <AccessibilityTips
                supportsHighContrastMode
                elementType="dialog"
              />
            </Box>
            <Box ml={1} sx={{ transform: "translateY(2px)" }}>
              <HelpLinkIcon title="Fieldmark help" link="#" />
            </Box>
          </Toolbar>
        </AppBar>
        <Box sx={{ display: "flex", minHeight: 0 }}>
          <DialogContent>
            <Grid
              container
              direction="column"
              spacing={2}
              sx={{ height: "100%", flexWrap: "nowrap" }}
            >
              <Grid item>
                <Typography variant="h3">Import from Fieldmark</Typography>
              </Grid>
              <Grid item>
                <Typography variant="body2">
                  Choose a Fieldmark notebook to import into Inventory. The new
                  list container will be placed on your bench.
                </Typography>
                <Typography variant="body2">
                  See <Link href="#">docs.fieldmark.au</Link> and our{" "}
                  <Link href={"#"}>Fieldmark integration docs</Link> for more.
                </Typography>
              </Grid>
            </Grid>
          </DialogContent>
        </Box>
      </Dialog>
    </ThemeProvider>
  );
}
