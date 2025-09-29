import React, { useContext } from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import Chip from "@mui/material/Chip";
import RecordIcon from "./RecordTypeIcon";
import NavigateContext from "../stores/contexts/Navigate";
import AnalyticsContext from "../stores/contexts/Analytics";
import { type LinkableRecord } from "../stores/definitions/LinkableRecord";

const useStyles = makeStyles({
  name: "GlobalId",
})((theme) => ({
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
    height: theme.spacing(3),
    fontWeight: theme.typography.fontWeightRegular,
    "&:focus-visible": {
      outline: `2px solid ${theme.palette.primary.main}`,
    },
    transitionDuration: "500ms",
  },
}));

type GlobalIdArgs = {
  record: LinkableRecord;
  size?: "medium" | "small";
  onClick?: () => void;
};

function GlobalId({ record, onClick }: GlobalIdArgs): React.ReactNode {
  const { classes } = useStyles();
  const { useNavigate } = useContext(NavigateContext);
  const { trackEvent } = useContext(AnalyticsContext);
  const navigate = useNavigate();

  const handleClick = (e: React.MouseEvent) => {
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
      /* this is how right-click copy works */
      {...(record.permalinkURL !== null ? { href: record.permalinkURL } : {})}
      label={record.globalId}
      icon={<RecordIcon record={record} aria-hidden={true} />}
      clickable
      onClick={handleClick}
      color="default"
    />
  );
}

export default observer(GlobalId);
