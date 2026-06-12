import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import type React from "react";
import { useId, useState } from "react";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import type { Container } from "../../../../stores/definitions/Container";
import GridDimensions from "./GridDimensions";
import GridLayoutConfig from "./GridLayoutConfig";

type GridDimensionsAndLayoutArgs = {
  container: Container;
};
export default function GridDimensionsAndLayout({ container }: GridDimensionsAndLayoutArgs): React.ReactNode {
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
              <IconButton onClick={() => setOpen(!open)} aria-label="Expand grid dimension controls group">
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
