//@flow

import React, { type Node, useState, useEffect, useContext } from "react";
import Box from "@mui/material/Box";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import Grid from "@mui/material/Grid";
import { makeStyles } from "tss-react/mui";
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

export opaque type FormSectionError = [
  Set<string>,
  (Set<string> | ((Set<string>) => Set<string>)) => void
];

export function useFormSectionError({
  editing,
  globalId,
}: {
  editing: boolean,
  globalId: ?GlobalId,
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
  value: boolean
): void {
  if (value) {
    errors.add(error);
    setErrors(errors);
  } else {
    errors.delete(error);
    setErrors(errors);
  }
}

const useStyles = makeStyles()((theme, { error, recordType }) => ({
  title: {
    wordBreak: "break-word",
    color: error ? theme.palette.error.main : "initial",
  },
  badge: {
    transitionDuration: window.matchMedia("(prefers-reduced-motion: reduce)")
      .matches
      ? "0s"
      : "225ms",
  },
  box: {
    backgroundColor: theme.palette.record[recordType].lighter,
  },
}));

type StepperPanelHeaderArgs = {|
  onToggle: (boolean) => void,
  open: boolean,
  title: Node,
  formSectionError?: FormSectionError,
  id: string,
  recordType: AllowedFormTypes,
  icon?: string,
|};

function StepperPanelHeader_({
  onToggle,
  open,
  title,
  formSectionError,
  id,
  recordType,
  icon,
}: StepperPanelHeaderArgs): Node {
  const numberInErrorState = formSectionError ? formSectionError[0].size : 0;
  const showErrorIndicator = numberInErrorState > 0 && !open;
  const { classes } = useStyles({ error: showErrorIndicator, recordType });
  const [allBtn, setAllBtn] = useState(false);
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");
  const { setAllExpanded } = formSectionContext;
  const theme = useTheme();

  useEffect(() => {
    if (allBtn) {
      setTimeout(() => setAllBtn(false), 5000);
    }
  }, [allBtn]);

  return (
    <Box
      p={1}
      onClick={() => {
        onToggle(!open);
        setAllBtn(true);
      }}
      className={classes.box}
    >
      <Grid container direction="row" spacing={1} alignItems="center">
        <Grid item>
          <IconButtonWithTooltip
            title={open ? "Collapse section" : "Expand section"}
            onClick={() => {
              onToggle(!open);
              setAllBtn(true);
            }}
            size="small"
            icon={
              <Badge
                badgeContent={numberInErrorState}
                color="error"
                invisible={open}
                classes={{ badge: classes.badge }}
              >
                <ExpandCollapseIcon open={open} />
              </Badge>
            }
          />
        </Grid>
        <Grid item>
          <Heading variant="h6" className={classes.title} id={id}>
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
        <Grid item style={{ flexGrow: 1 }}></Grid>
        <Grid item>
          <Slide direction="left" in={allBtn}>
            <Chip
              label={open ? "Expand All" : "Collapse All"}
              onClick={preventEventBubbling(() => {
                setAllExpanded(recordType, open);
              })}
            />
          </Slide>
        </Grid>
      </Grid>
    </Box>
  );
}

export const StepperPanelHeader = (observer(
  StepperPanelHeader_
): typeof StepperPanelHeader_);
