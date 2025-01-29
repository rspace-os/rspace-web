// @flow

import React, { type Node, type ComponentType, useId, useContext } from "react";
import { Observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";
import Collapse from "@mui/material/Collapse";
import { makeStyles } from "tss-react/mui";
import Divider from "@mui/material/Divider";
import Box from "@mui/material/Box";
import {
  StepperPanelHeader,
  type FormSectionError,
} from "./StepperPanelHeader";
import FormSectionsContext, {
  type AllowedFormTypes,
} from "../../../stores/contexts/FormSections";
import Stack from "@mui/material/Stack";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";
import { useTheme } from "@mui/material/styles";

const useStyles = makeStyles()((theme) => ({
  container: {
    flexWrap: "nowrap",
    backgroundColor: theme.palette.background.alt,
    overflowX: "hidden",
  },
  collapse: {
    transitionDuration: window.matchMedia("(prefers-reduced-motion: reduce)")
      .matches
      ? "0s !important"
      : "initial",
  },
}));

type StepperPanelArgs = {|
  title: Node,
  sectionName: string,
  children: Node,
  formSectionError?: FormSectionError,
  recordType: AllowedFormTypes,
  icon?: string,
  thickBorder?: boolean,
|};

const StepperPanel: ComponentType<StepperPanelArgs> = React.forwardRef(
  (
    {
      title,
      children,
      sectionName,
      formSectionError,
      recordType,
      icon,
      thickBorder,
    }: StepperPanelArgs,
    ref
  ) => {
    const { classes } = useStyles();
    const theme = useTheme();
    const headingId = useId();
    const formSectionContext = useContext(FormSectionsContext);
    if (!formSectionContext)
      throw new Error("FormSectionContext is required by StepperPanel");
    const { isExpanded, setExpanded } = formSectionContext;

    return (
      <Observer>
        {() => (
          <Grid
            container
            direction="column"
            className={classes.container}
            ref={ref}
            role="region"
            aria-labelledby={headingId}
          >
            <Grid item>
              <StepperPanelHeader
                onToggle={(value) =>
                  setExpanded(recordType, sectionName, value)
                }
                open={isExpanded(recordType, sectionName)}
                title={title}
                formSectionError={formSectionError}
                id={headingId}
                recordType={recordType}
                icon={icon}
              />
            </Grid>
            <Collapse
              in={isExpanded(recordType, sectionName)}
              className={classes.collapse}
            >
              <Box p={2} pt={1}>
                <Grid item>
                  <HeadingContext>
                    <Stack spacing={3} sx={{ mt: 0.5 }}>
                      {children}
                    </Stack>
                  </HeadingContext>
                </Grid>
              </Box>
            </Collapse>
            <Grid item>
              <Divider
                orientation="horizontal"
                sx={
                  thickBorder
                    ? {
                        borderWidth: "1px",
                        borderColor: theme.palette.record[recordType].bg,
                      }
                    : {}
                }
              />
            </Grid>
          </Grid>
        )}
      </Observer>
    );
  }
);

StepperPanel.displayName = "StepperPanel";
export default StepperPanel;
