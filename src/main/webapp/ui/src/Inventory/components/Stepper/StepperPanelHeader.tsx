import React, { useState, useEffect, useContext } from "react";
import Box from "@mui/material/Box";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import Grid from "@mui/material/Grid";
import Badge from "@mui/material/Badge";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import { type GlobalId } from "../../../stores/definitions/BaseRecord";
import Slide from "@mui/material/Slide";
import Chip from "@mui/material/Chip";
import { preventEventBubbling } from "../../../util/Util";
import FormSectionsContext, {
  type AllowedFormTypes,
} from "../../../stores/contexts/FormSections";
import { observer } from "mobx-react-lite";
import { Heading } from "../../../components/DynamicHeadingLevel";
import { useTheme } from "@mui/material/styles";
import RecordTypeIcon from "../../../components/RecordTypeIcon";

export type FormSectionError = [
  Set<string>,
  React.Dispatch<React.SetStateAction<Set<string>>>,
];

export function useFormSectionError({
  editing,
  globalId,
}: {
  editing: boolean;
  globalId: GlobalId | null;
}): FormSectionError {
  const [errors, setErrors] = useState<Set<string>>(new Set());

  useEffect(() => {
    setErrors(new Set());
  }, [editing, globalId]);

  return [errors, setErrors];
}

export function setFormSectionError(
  [errors, setErrors]: FormSectionError,
  error: string,
  value: boolean,
): void {
  if (value) {
    errors.add(error);
    setErrors(errors);
  } else {
    errors.delete(error);
    setErrors(errors);
  }
}

type StepperPanelHeaderArgs = {
  onToggle: (newOpen: boolean) => void;
  open: boolean;
  title: React.ReactNode;
  formSectionError?: FormSectionError;
  id: string;
  recordType: AllowedFormTypes;
  icon?: string;
};

function StepperPanelHeader_({
  onToggle,
  open,
  title,
  formSectionError,
  id,
  recordType,
  icon,
}: StepperPanelHeaderArgs): React.ReactNode {
  const numberInErrorState = formSectionError ? formSectionError[0].size : 0;
  const showErrorIndicator = numberInErrorState > 0 && !open;
  const [allBtn, setAllBtn] = useState(false);
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");
  const theme = useTheme();

  useEffect(() => {
    if (allBtn) {
      setTimeout(() => setAllBtn(false), 5000);
    }
  }, [allBtn]);

  return (
    <Box
      onClick={() => {
        onToggle(!open);
        setAllBtn(true);
      }}
      sx={{
        p: 1,
        backgroundColor: theme.palette.record[recordType].lighter,
      }}
    >
      <Grid container direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <Grid>
          <IconButtonWithTooltip
            title={open ? "Collapse section" : "Expand section"}
            onClick={(e) => {
              e.stopPropagation();
              onToggle(!open);
              setAllBtn(true);
            }}
            size="small"
            icon={
              <Badge
                badgeContent={numberInErrorState}
                color="error"
                invisible={open}
                  slotProps={{
                    badge: {
                      sx: {
                        transitionDuration: window.matchMedia(
                          "(prefers-reduced-motion: reduce)",
                        ).matches
                          ? "0s"
                          : "225ms",
                      },
                    },
                  }}
              >
                <ExpandCollapseIcon open={open} />
              </Badge>
            }
          />
        </Grid>
        <Grid>
          <Heading
            variant="h6"
            id={id}
            sx={{
              wordBreak: "break-word",
              color: showErrorIndicator ? theme.palette.error.main : "initial",
            }}
          >
            {icon && (
              <RecordTypeIcon
                record={{
                  recordTypeLabel: "",
                  iconName: icon,
                }}
                color="black"
                style={{
                  transform: "scale(1)",
                  opacity: 0.8,
                  marginRight: theme.spacing(1),
                }}
              />
            )}
            {title}
          </Heading>
        </Grid>
        <Grid sx={{ flexGrow: 1 }}></Grid>
        <Grid>
          <Slide direction="left" in={allBtn}>
            <Chip
              label={open ? "Expand All" : "Collapse All"}
              onClick={preventEventBubbling(() => {
                formSectionContext.setAllExpanded(recordType, open);
              })}
            />
          </Slide>
        </Grid>
      </Grid>
    </Box>
  );
}

export const StepperPanelHeader = observer(StepperPanelHeader_);
