import React from "react";
import { observer } from "mobx-react-lite";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Divider from "@mui/material/Divider";
import { Heading } from "@/components/DynamicHeadingLevel";

type TitledBoxArgs = {
  title?: React.ReactNode;
  children?: React.ReactNode;
  allowOverflow?: boolean;
  border?: boolean;
};

function TitledBox({
  title,
  children,
  allowOverflow = true,
  border = false,
}: TitledBoxArgs): React.ReactNode {
  return (
    <Grid
      container
      sx={(theme) => ({
        flexDirection: "column",
        flexWrap: "nowrap",
        maxHeight: "100%",
        backgroundColor: "white !important",
        ...(border
          ? {
              border: theme.borders.section,
              my: 1,
              borderRadius: theme.spacing(0.5),
            }
          : {}),
      })}
    >
      {title !== null && typeof title !== "undefined" && (
        <>
          <Grid sx={{ px: 2, py: 1 }}>
            <Grid container direction="row" sx={{ alignItems: "center" }}>
              <Grid style={{ flexGrow: 1 }}>
                <Heading
                  variant="h5"
                  sx={{
                    wordBreak: "break-word",
                  }}
                >
                  {title}
                </Heading>
              </Grid>
            </Grid>
          </Grid>
          <Grid>
            <Divider orientation="horizontal" />
          </Grid>
        </>
      )}
      <Box
        component={Grid}
        sx={(theme) => ({
          p: theme.spacing(2),
          overflow: "auto",
          overflowX: allowOverflow ? "auto" : "hidden",
        })}
      >
        {children}
      </Box>
    </Grid>
  );
}

/**
 * This component defines a box with an optional title and a body.  It is
 * useful in laying out pages that have multiple sections that the user should
 * browse sequentially.
 */
export default observer(TitledBox);
