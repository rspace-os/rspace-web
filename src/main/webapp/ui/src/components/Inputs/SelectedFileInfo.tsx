import Chip from "@mui/material/Chip";
import React from "react";
import { match } from "../../util/Util";
import { useTheme } from "@mui/material/styles";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";

function StatusChip({
  error,
  loading,
  selectedFilename,
}: {
  error: boolean;
  selectedFilename: string | null;
  loading: boolean;
}): React.ReactNode {
  const theme = useTheme();
  const color = match<void, string>([
    [() => loading, theme.palette.text.secondary],
    [() => error, theme.palette.primary.contrastText],
    [() => Boolean(selectedFilename), theme.palette.primary.contrastText],
    [() => true, theme.palette.text.secondary],
  ])();
  const backgroundColor = match<void, string>([
    [() => loading, "#e0e0e0"],
    [() => error, theme.palette.error.main],
    [() => Boolean(selectedFilename), theme.palette.success.main],
    [() => true, "#e0e0e0"],
  ])();
  const spinnerIcon = <FontAwesomeIcon icon={faSpinner} spin size="sm" />;
  const label = match<void, React.ReactNode>([
    [() => loading, spinnerIcon],
    [() => error, "Invalid file."],
    [() => Boolean(selectedFilename), selectedFilename],
    [() => true, "None"],
  ])();
  const iconStyle: React.CSSProperties = {
    width: 18,
    height: 18,
    marginLeft: 2,
    color: theme.palette.primary.contrastText,
  };
  const avatar = match<void, React.ReactElement | null>([
    [() => loading, null],
    [() => error, <ErrorIcon key="erroricon" style={iconStyle} />],
    [
      () => Boolean(selectedFilename),
      <CheckCircleIcon key="checkicon" style={iconStyle} />,
    ],
    [() => true, null],
  ])();
  return (
    <Chip
      label={label}
      {...(avatar ? { avatar } : {})}
      sx={{
        height: 20,
        letterSpacing: "0.04em",
        color,
        backgroundColor,
        transition: "background-color 0s",
      }}
      slotProps={{
        label: {
          sx: {
            pl: loading ? "3px" : "10px",
            pr: loading ? "3px" : "10px",
            fontSize: loading ? "1.25em" : "inherit",
          },
        },
      }}
    />
  );
}

type SelectedFileInfoArgs = {
  selectedFilename: string | null;
  error: boolean;
  loading: boolean;
};

function SelectedFileInfo({
  selectedFilename,
  error,
  loading,
}: SelectedFileInfoArgs): React.ReactNode {
  const theme = useTheme();
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0.5, 2),
      }}
    >
      <dt style={{ color: theme.palette.text.secondary }}>File selected:</dt>
      <dd style={{ marginLeft: theme.spacing(1) }}>
        <StatusChip
          selectedFilename={selectedFilename}
          error={error}
          loading={loading}
        />
      </dd>
    </div>
  );
}

export default SelectedFileInfo;
