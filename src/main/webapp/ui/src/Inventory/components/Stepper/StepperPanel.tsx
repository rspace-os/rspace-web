import React, { useId, useContext } from "react";
import { Observer } from "mobx-react-lite";
import Collapse from "@mui/material/Collapse";
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
  React.ElementRef<typeof Box>,
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
    ref,
  ) => {
    const theme = useTheme();
    const headingId = useId();
    const formSectionContext = useContext(FormSectionsContext);
    if (!formSectionContext)
      throw new Error("FormSectionContext is required by StepperPanel");
    return (
      <Observer>
        {() => (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              flexWrap: "nowrap",
              backgroundColor: theme.palette.background.alt,
              overflowX: "hidden",
            }}
            ref={ref}
            role="region"
            aria-labelledby={headingId}
          >
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
            <Collapse
              in={formSectionContext.isExpanded(recordType, sectionName)}
              sx={{
                transitionDuration: window.matchMedia(
                  "(prefers-reduced-motion: reduce)",
                ).matches
                  ? "0s !important"
                  : "initial",
              }}
            >
              <Box sx={{ p: 2, pt: 1 }}>
                <HeadingContext>
                  <Stack spacing={3} sx={{ mt: 0.5 }}>
                    {children}
                  </Stack>
                </HeadingContext>
              </Box>
            </Collapse>
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
          </Box>
        )}
      </Observer>
    );
  },
);

StepperPanel.displayName = "StepperPanel";
export default StepperPanel;
