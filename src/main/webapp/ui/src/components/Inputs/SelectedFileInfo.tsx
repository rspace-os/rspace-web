import Chip from "@mui/material/Chip";
import React from "react";
import clsx from "clsx";
import { match } from "../../util/Util";
import { withStyles } from "Styles";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";

const StatusChip = withStyles<
  { error: boolean; selectedFilename: string | null; loading: boolean },
  { root: string; label: string; avatar: string }
>((theme, { loading, error, selectedFilename }) => ({
  root: {
    height: 20,
    letterSpacing: "0.04em",
    color: match<void, string>([
      [() => loading, theme.palette.text.secondary],
      [() => error, theme.palette.primary.contrastText],
      [() => Boolean(selectedFilename), theme.palette.primary.contrastText],
      [() => true, theme.palette.text.secondary],
    ])(),
    backgroundColor: match<void, string>([
      [() => loading, "#e0e0e0"],
      [() => error, theme.palette.error.main],
      [() => Boolean(selectedFilename), theme.palette.success.main],
      [() => true, "#e0e0e0"],
    ])(),
    transition: "background-color 0s",
  },
  label: {
    paddingLeft: loading ? 3 : 10,
    paddingRight: loading ? 3 : 10,
    fontSize: loading ? "1.25em" : "inherit",
  },
  avatar: {
    width: "18px !important",
    height: "18px !important",
    marginLeft: "2px !important",
    color: `${theme.palette.primary.contrastText} !important`,
  },
}))(({ classes, error, loading, selectedFilename }) => {
  const spinnerIcon = <FontAwesomeIcon icon={faSpinner} spin size="sm" />;
  const label = match<void, React.ReactNode>([
    [() => loading, spinnerIcon],
    [() => error, "Invalid file."],
    [() => Boolean(selectedFilename), selectedFilename],
    [() => true, "None"],
  ])();
  const avatar = match<void, React.ReactElement | null>([
    [() => loading, null],
    [() => error, <ErrorIcon key="erroricon" className={classes.avatar} />],
    [
      () => Boolean(selectedFilename),
      <CheckCircleIcon key="checkicon" className={classes.avatar} />,
    ],
    [() => true, null],
  ])();
  return (
    <Chip classes={classes} label={label} {...(avatar ? { avatar } : {})} />
  );
});

type SelectedFileInfoArgs = {
  selectedFilename: string | null;
  error: boolean;
  loading: boolean;
};

const SelectedFileInfo = withStyles<
  SelectedFileInfoArgs,
  { details: string; detailsLabel: string; filename: string }
>((theme) => ({
  details: {
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(0.5, 2),
  },
  detailsLabel: {
    color: theme.palette.text.secondary,
  },
  filename: {
    marginLeft: theme.spacing(1),
  },
}))(({ selectedFilename, classes, error, loading }) => (
  <div className={classes.details}>
    <dt className={classes.detailsLabel}>File selected:</dt>
    <dd className={clsx(classes.filename)}>
      <StatusChip
        selectedFilename={selectedFilename}
        error={error}
        loading={loading}
      />
    </dd>
  </div>
));

SelectedFileInfo.displayName = "SelectedFileInfo ";
export default SelectedFileInfo;
