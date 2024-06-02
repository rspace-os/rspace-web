//@flow

import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Grid from "@mui/material/Grid";
import NumberedLocation from "../NumberedLocation";
import React, { type Node, type ComponentType } from "react";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { preventEventBubbling } from "../../../../../util/Util";
import Result from "../../../../../stores/models/Result";
import { type Location } from "../../../../../stores/definitions/Container";
import useNavigateHelpers from "../../../../useNavigateHelpers";

const useStyles = makeStyles()(() => ({
  root: {
    display: "flex",
    width: 400,
  },
  rootFullWidth: {
    width: "100%",
  },
  details: {
    display: "flex",
    flexDirection: "column",
  },
  content: {
    flex: "1 0 auto",
    paddingBottom: "8px !important",
  },
  image: {
    objectFit: "contain",
    height: "initial",
    borderRadius: 3,
    maxHeight: 150,
    maxWidth: 150,
  },
  imageWrapper: {
    padding: 4,
  },
  fullHeight: {
    height: "100%",
  },
}));

const ActionButton = ({
  children,
  onClick,
  disabled = false,
}: {
  children: Node,
  onClick: (Event) => void,
  disabled?: boolean,
}) => (
  <Button
    color="primary"
    disabled={disabled}
    onClick={onClick}
    size="small"
    style={{ pointerEvents: "initial" }}
  >
    {children}
  </Button>
);

type SummaryCardArgs = {|
  editable?: boolean,
  fullWidth: boolean,
  location: Location,
  number: number,
  onClick?: () => void,
  onRemove: () => void,
  selected?: number,
|};

function SummaryCard({
  editable = false,
  number,
  location,
  onRemove,
  selected,
  onClick,
  fullWidth = false,
}: SummaryCardArgs): Node {
  const { classes } = useStyles();
  const { navigateToRecord } = useNavigateHelpers();

  const helperText = location.hasContent
    ? ""
    : "This location can be chosen as the destination in a move operation.";

  const hasImage =
    location.content instanceof Result && Boolean(location.content.image);

  return (
    <Card
      className={`${classes.root} ${fullWidth ? classes.rootFullWidth : ""}`}
      onClick={onClick}
      variant="outlined"
    >
      <Grid container>
        <Grid item xs={hasImage ? 7 : 12}>
          <Grid container direction="column" className={classes.fullHeight}>
            <Box flexGrow={1}>
              <CardContent className={classes.content}>
                <Typography gutterBottom variant="h5" component="h2">
                  <NumberedLocation
                    number={number}
                    inline
                    selected={selected === number}
                  />
                  {location.name ?? <i>Empty Location</i>}
                </Typography>
                <Typography
                  variant="body2"
                  color="textSecondary"
                  component="em"
                  gutterBottom
                >
                  {helperText}
                </Typography>
              </CardContent>
            </Box>
            <Grid item>
              <CardActions>
                {editable && !location.hasContent && (
                  <ActionButton onClick={preventEventBubbling(onRemove)}>
                    Remove
                  </ActionButton>
                )}
                {!editable && location.content && (
                  <ActionButton
                    disabled={!location.hasContent}
                    onClick={(event: Event) => {
                      event.stopPropagation();
                      if (location.content)
                        void navigateToRecord(location.content);
                    }}
                  >
                    Open
                  </ActionButton>
                )}
              </CardActions>
            </Grid>
          </Grid>
        </Grid>
        {hasImage && (
          <Grid item xs={5}>
            <div className={classes.imageWrapper}>
              <CardMedia
                className={classes.image}
                component="img"
                height="140"
                src={location.content?.image ?? ""}
                alt={location.content?.name ?? "Empty Location"}
              />
            </div>
          </Grid>
        )}
      </Grid>
    </Card>
  );
}

export default (observer(SummaryCard): ComponentType<SummaryCardArgs>);
