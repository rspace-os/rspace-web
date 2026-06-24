import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import { useTranslation } from "react-i18next";
import { match } from "../../util/Util";

function StatusChip({
  error,
  loading,
  selectedFilename,
}: {
  error: boolean;
  selectedFilename: string | null;
  loading: boolean;
}): React.ReactNode {
  const { t } = useTranslation("common");
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
    [() => error, t("inputs.selectedFileInfo.invalidFile")],
    [() => Boolean(selectedFilename), selectedFilename],
    [() => true, t("values.none")],
  ])();
  const iconStyle = {
    width: 18,
    height: 18,
    marginLeft: "2px",
    color: theme.palette.primary.contrastText,
  };
  const avatar = match<void, React.ReactElement | null>([
    [() => loading, null],
    [() => error, <ErrorIcon key="erroricon" sx={iconStyle} />],
    [() => Boolean(selectedFilename), <CheckCircleIcon key="checkicon" sx={iconStyle} />],
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

function SelectedFileInfo({ selectedFilename, error, loading }: SelectedFileInfoArgs): React.ReactNode {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0.5, 2),
      }}
    >
      <Box component="dt" sx={{ color: theme.palette.text.secondary }}>
        File selected:
      </Box>
      <Box component="dd" sx={{ marginLeft: theme.spacing(1) }}>
        <StatusChip selectedFilename={selectedFilename} error={error} loading={loading} />
      </Box>
    </Box>
  );
}

export default SelectedFileInfo;
