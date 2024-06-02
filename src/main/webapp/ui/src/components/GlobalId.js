//@flow strict

import React, { useContext, type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import Chip from "@mui/material/Chip";
import RecordIcon from "./RecordTypeIcon";
import NavigateContext from "../stores/contexts/Navigate";
import AnalyticsContext from "../stores/contexts/Analytics";
import { type LinkableRecord } from "../stores/definitions/LinkableRecord";

const useStyles = makeStyles({ name: "GlobalId" })((theme, { variant }) => ({
  globalId: {
    display: "flex",
    flexDirection: "row",
  },
  chip: {
    /*
     * MUI chips prevent user selection of the text within,
     * but we want to allow text selection so that when the Global ID link
     * is right-clicked the user may copy the Global ID as text in addition
     * to the permalink that clicking it would send them to.
     */
    userSelect: "unset",

    paddingLeft: theme.spacing(1.5),
    backgroundColor:
      variant === "white" ? "rgba(255,255,255,0.15)" : theme.palette.grey[200],
    color: variant === "white" ? "white" : theme.palette.grey[800],
    height: theme.spacing(3),
    fontWeight: theme.typography.fontWeightRegular,
    "&:hover, &:focus": {
      backgroundColor:
        variant === "white"
          ? "rgba(255,255,255,0.22)"
          : theme.palette.grey[300],
    },
    "&:focus-visible": {
      outline: `2px solid ${theme.palette.primary.main}`,
    },
    transitionDuration: 500,
  },
}));

type GlobalIdArgs = {|
  record: LinkableRecord,
  size?: "medium" | "small",
  variant?: "color" | "white",
  onClick?: () => void,
|};

function GlobalId({ record, variant = "color", onClick }: GlobalIdArgs): Node {
  const { classes } = useStyles({ variant });
  const { useNavigate } = useContext(NavigateContext);
  const { trackEvent } = useContext(AnalyticsContext);
  const navigate = useNavigate();

  const handleClick = (e: Event) => {
    e.preventDefault();
    e.stopPropagation();
    if (
      record.permalinkURL !== null &&
      typeof record.permalinkURL !== "undefined"
    ) {
      navigate(record.permalinkURL);
      trackEvent("GlobalIdClicked");
      onClick?.();
    }
  };

  return (
    <Chip
      className={classes.chip}
      component="a"
      href={record.permalinkURL /* this is how right-click copy works */}
      label={record.globalId}
      icon={
        <RecordIcon
          color={variant === "white" ? "white" : null}
          record={record}
          aria-hidden={true}
        />
      }
      onClick={handleClick}
      color="default"
    />
  );
}

export default (observer(GlobalId): ComponentType<GlobalIdArgs>);
