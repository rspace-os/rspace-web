// @flow

import React, { type Node, type ComponentType, type ElementProps } from "react";
import { observer } from "mobx-react-lite";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";

const Header = withStyles<
  {| strikeThroughTitle: boolean, ...ElementProps<typeof CardHeader> |},
  {
    root: string,
    title: string,
    subheader: string,
    avatar: string,
    action: string,
  }
>((theme, { strikeThroughTitle }) => ({
  root: {
    padding: theme.spacing(0.5),
  },
  title: {
    fontWeight: "600",
    wordBreak: "break-word",
    textDecorationLine: strikeThroughTitle ? "line-through" : "none",
  },
  subheader: {
    fontSize: "0.8em",
    lineHeight: theme.spacing(1.5),
  },
  avatar: {
    margin: theme.spacing(0, 1.5, 0, 0.75),
  },
  action: {
    alignSelf: "unset",
    margin: 0,
  },
}))(({ strikeThroughTitle, ...rest }) => <CardHeader {...rest} />); //eslint-disable-line no-unused-vars

type CardStructureArgs = {|
  content: Node,
  contentFooter?: Node,
  headerAction?: Node,
  headerAvatar?: Node,
  image: Node,
  subheader: Node | string,
  title: Node,
  onClick?: () => void,
  className?: string,
  deleted?: boolean,
|};

const useStyles = makeStyles()((theme, { deleted }) => ({
  root: {
    "&:hover": {
      backgroundColor: theme.palette.background.default,
    },
    height: "100%",
  },
  icon: {
    color: theme.palette.text.secondary,
    transform: "scale(0.8) translateY(8px)",
  },
  label: {
    display: "inline-block",
    color: theme.palette.text.secondary,
    paddingLeft: 2,
    paddingRight: 8,
  },
  cardContent: {
    padding: theme.spacing(1, 1, 0, 1),
  },
  imageWrapper: {
    filter: deleted ? "grayscale(1)" : "none",
    opacity: deleted ? 0.6 : 1.0,
  },
}));

function CardStructure({
  content,
  contentFooter,
  headerAction,
  headerAvatar,
  image,
  subheader,
  title,
  onClick,
  className,
  deleted = false,
}: CardStructureArgs): Node {
  const { classes } = useStyles({ deleted });

  return (
    <Card className={clsx(classes.root, className)} role="region">
      <Grid container style={{ height: "100%" }}>
        <Grid item xs={4} className={classes.imageWrapper}>
          {image}
        </Grid>
        {/* img has separate onClick */}
        <Grid item xs={8} onClick={onClick} container direction="column">
          <Grid item>
            <Header
              strikeThroughTitle={deleted}
              title={title}
              subheader={subheader}
              avatar={headerAvatar}
              action={headerAction}
            />
          </Grid>
          <Grid item>
            <Divider orientation="horizontal" />
          </Grid>
          <Grid item>
            <CardContent className={classes.cardContent}>
              <Grid container direction="column">
                <Grid item>{content}</Grid>
              </Grid>
            </CardContent>
          </Grid>
          {Boolean(contentFooter) && (
            <Grid item sx={{ mt: "auto" }}>
              {contentFooter}
            </Grid>
          )}
        </Grid>
      </Grid>
    </Card>
  );
}

export default (observer(CardStructure): ComponentType<CardStructureArgs>);
