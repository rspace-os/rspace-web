import React, { useId, useContext } from "react";
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

type StepperPanelArgs = {
  title: React.ReactNode;
  sectionName: string;
  children: React.ReactNode;
  formSectionError?: FormSectionError;
  recordType: AllowedFormTypes;
  icon?: string;
  thickBorder?: boolean;
};

const StepperPanel = React.forwardRef<
  React.ElementRef<typeof Grid>,
  StepperPanelArgs
>(
  (
    {
      title,
      children,
      sectionName,
      formSectionError,
      recordType,
      icon,
      thickBorder,
    },
    ref
  ) => {
    const { classes } = useStyles();
    const theme = useTheme();
    const headingId = useId();
    const formSectionContext = useContext(FormSectionsContext);
    if (!formSectionContext)
      throw new Error("FormSectionContext is required by StepperPanel");
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
                  formSectionContext.setExpanded(recordType, sectionName, value)
                }
                open={formSectionContext.isExpanded(recordType, sectionName)}
                title={title}
                formSectionError={formSectionError}
                id={headingId}
                recordType={recordType}
                icon={icon}
              />
            </Grid>
            <Collapse
              in={formSectionContext.isExpanded(recordType, sectionName)}
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
