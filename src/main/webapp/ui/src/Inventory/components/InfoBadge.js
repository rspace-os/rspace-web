// @flow

import React, { type Node, type ComponentType, type ElementProps } from "react";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import Tooltip from "@mui/material/Tooltip";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import NotInterestedIcon from "@mui/icons-material/NotInterested";
import PadlockIcon from "../../assets/graphics/PadlockIcon";
import PeopleIcon from "../../assets/graphics/PeopleIcon";
import { observer } from "mobx-react-lite";
import { type Record } from "../../stores/definitions/Record";
import clsx from "clsx";

const CustomTooltip = withStyles<
  ElementProps<typeof Tooltip>,
  { tooltip: string }
>((theme) => ({
  tooltip: {
    maxWidth: `calc(100vw - ${theme.spacing(2)})`,
    padding: 0,
  },
}))(Tooltip);

const useStyles = makeStyles()((theme, { deleted }) => ({
  icon: {
    height: 20,
    width: 20,
    borderRadius: 10,
    color: "white",
    backgroundColor: deleted
      ? theme.palette.deletedGrey
      : theme.palette.primary.main,
  },
  absolute: {
    position: "absolute",
    top: 0,
    right: -10,
  },
  inline: {
    marginBottom: -5,
    marginRight: 5,
  },
}));

type InfoBadgeArgs = {|
  inline?: boolean,
  children: Node,
  record: Record,
|};

function InfoBadge({ inline = false, children, record }: InfoBadgeArgs): Node {
  const { readAccessLevel, deleted } = record;
  const { classes } = useStyles({ deleted });

  const iconProps = {
    className: clsx(classes.icon, inline && classes.inline),
  };

  return (
    <CustomTooltip
      onClick={(e) => e.stopPropagation()}
      disableFocusListener
      enterDelay={300}
      enterTouchDelay={300}
      leaveTouchDelay={1200}
      title={children}
    >
      <span className={clsx(!inline && classes.absolute)}>
        {readAccessLevel === "public" ? (
          <NotInterestedIcon {...iconProps} />
        ) : readAccessLevel === "limited" ? (
          <PadlockIcon {...iconProps} />
        ) : record.owner && !record.currentUserIsOwner ? (
          <PeopleIcon {...iconProps} />
        ) : (
          <InfoOutlinedIcon {...iconProps} />
        )}
      </span>
    </CustomTooltip>
  );
}

export default (observer(InfoBadge): ComponentType<InfoBadgeArgs>);
