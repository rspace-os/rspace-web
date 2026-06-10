import React, { useState, useId } from "react";
import GridDimensions from "./GridDimensions";
import GridLayoutConfig from "./GridLayoutConfig";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import Card from "@mui/material/Card";
import Box from "@mui/material/Box";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import { type Container } from "../../../../stores/definitions/Container";
type GridDimensionsAndLayoutArgs = {
  container: Container;
};
export default function GridDimensionsAndLayout({
  container,
}: GridDimensionsAndLayoutArgs): React.ReactNode {
  const [open, setOpen] = useState(false);
  const headingId = useId();
  const contentId = useId();
  return (
    <>
      {container.state === "create" && <GridDimensions />}
      <Box
        sx={{
          my: 1,
        }}
      >
        <Card variant="outlined" role="region" aria-labelledby={headingId}>
          <CardHeader
            sx={{ height: 48, p: "0 0 0 12px" }}
            slotProps={{
              action: {
                sx: {
                  m: 0,
                  height: "100%",
                  alignItems: "center",
                  display: "flex",
                },
              },
              title: { variant: "body1", id: headingId },
            }}
            title="Configure Grid Labels"
            onClick={() => setOpen(!open)}
            aria-controls={contentId}
            aria-expanded={open}
            action={
              <IconButton
                onClick={() => setOpen(!open)}
                aria-label="Expand grid dimension controls group"
              >
                <ExpandCollapseIcon open={open} />
              </IconButton>
            }
          />
          <Collapse in={open} id={contentId}>
            <CardContent sx={{ pb: "8px !important" }}>
              <GridLayoutConfig container={container} />
            </CardContent>
          </Collapse>
        </Card>
      </Box>
    </>
  );
}
