import React, { useState, useId } from "react";
import GridDimensions from "./GridDimensions";
import GridLayoutConfig from "./GridLayoutConfig";
import { withStyles } from "Styles";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import Card from "@mui/material/Card";
import Box from "@mui/material/Box";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import { type Container } from "../../../../stores/definitions/Container";

const CustomHeader = withStyles<
  React.ComponentProps<typeof CardHeader> & {
    open: boolean;
    setOpen: React.Dispatch<React.SetStateAction<boolean>>;
    id: string;
    controls: string;
  },
  { root: string; action: string }
>(() => ({
  root: {
    height: 48,
    padding: "0 0 0 12px",
  },
  action: {
    margin: 0,
    height: "100%",
    alignItems: "center",
    display: "flex",
  },
}))(({ open, setOpen, classes, id, controls }) => (
  <CardHeader
    classes={classes}
    title="Configure Grid Labels"
    titleTypographyProps={{ variant: "body1", id }}
    onClick={() => setOpen(!open)}
    aria-controls={controls}
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
));

const CustomContent = withStyles<
  React.ComponentProps<typeof CardContent>,
  { root: string }
>((theme) => ({
  root: {
    paddingBottom: `${theme.spacing(1)} !important`,
  },
}))(CardContent);

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
      <Box my={1}>
        <Card variant="outlined" role="region" aria-labelledby={headingId}>
          <CustomHeader
            open={open}
            setOpen={setOpen}
            id={headingId}
            controls={contentId}
          />
          <Collapse in={open} id={contentId}>
            <CustomContent>
              <GridLayoutConfig container={container} />
            </CustomContent>
          </Collapse>
        </Card>
      </Box>
    </>
  );
}
