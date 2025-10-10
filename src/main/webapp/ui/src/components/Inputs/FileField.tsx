import React, { useState, useEffect, forwardRef } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import PublishOutlinedIcon from "@mui/icons-material/PublishOutlined";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import SelectedFileInfo from "./SelectedFileInfo";
import { readFileAsBinaryString } from "../../util/Util";
import InputBase from "@mui/material/InputBase";
import BigIconButton from "../BigIconButton";
import Grid from "@mui/material/Grid";

const useStyles = makeStyles()((theme) => ({
  root: {
    "& > *": {
      margin: theme.spacing(1),
    },
  },
  input: {
    display: "none",
  },
  alert: {
    marginTop: theme.spacing(1),
  },
  visibleInput: {
    flexWrap: "wrap",
    justifyContent: "center",
  },
}));

type ButtonThatTriggersInvisibleInputArgs = {
  buttonLabel: string;
  InputProps: {
    startAdornment?: React.ReactNode;
    endAdornment?: React.ReactNode;
  };
  disabled?: boolean;
  id: string;
  icon: React.ReactNode;
  explanatoryText?: string;
  containerProps?: React.ComponentProps<typeof Grid>;
};

const ButtonThatTriggersInvisibleInput = forwardRef<
  React.ElementRef<"label">,
  ButtonThatTriggersInvisibleInputArgs
>(
  (
    {
      buttonLabel,
      InputProps,
      disabled,
      id,
      icon,
      explanatoryText,
      containerProps,
    }: ButtonThatTriggersInvisibleInputArgs,
    ref,
  ) => (
    <Grid container spacing={1} {...containerProps}>
      {InputProps.startAdornment}
      <Grid item flexGrow={1}>
        <label htmlFor={id} ref={ref}>
          {/* These buttons are rendered as HTMLSpanElements so that
           * tapping them results in the click event bubbling up to the
           * HTMLLableElement above, which in turn triggers the
           * HTMLInputElement with type "file" that is rendered in this
           * file, below.
           */}
          {explanatoryText ? (
            <BigIconButton
              icon={icon}
              label={buttonLabel}
              explanatoryText={explanatoryText}
              component="span"
            />
          ) : (
            <Button
              size="large"
              color="primary"
              variant="outlined"
              component="span"
              fullWidth
              startIcon={icon}
              disabled={disabled}
            >
              {buttonLabel}
            </Button>
          )}
        </label>
      </Grid>
      {InputProps.endAdornment}
    </Grid>
  ),
);

ButtonThatTriggersInvisibleInput.displayName =
  "ButtonThatTriggersInvisibleInput";

export type FileFieldArgs = {
  // required
  accept: string;
  onChange: (event: { binaryString: string; file: File }) => void;

  // optional
  id?: string;
  InputProps?: {
    startAdornment?: React.ReactNode;
    endAdornment?: React.ReactNode;
  };
  buttonLabel?: string;
  datatestid?: string;
  disabled?: boolean;
  error?: boolean;
  icon?: React.ReactNode;
  loadedFile?: File | null;
  loading?: boolean;
  name?: string;
  showSelectedFilename?: boolean;
  value?: string;
  warningAlert?: string;
  explanatoryText?: string;
  containerProps?: React.ComponentProps<typeof Grid>;

  /*
   * This overrides the default button that triggers the opening of the
   * operating system's file picker. If it is set, then some of the props
   * listed above are ignored:
   *   - InputProps
   *   - buttonLabel
   *   - icon
   *   - warningAlert
   *   - explanatoryText
   *   - containerProps
   */
  triggerButton?: ({ id }: { id: string }) => React.ReactNode;
};

function FileField({
  disabled,
  value,
  onChange,
  name,
  accept,
  InputProps = {},
  id: passedId,
  buttonLabel = "Upload",
  icon = <PublishOutlinedIcon />,
  warningAlert = "",
  showSelectedFilename = false,
  loading = false,
  error = false,
  datatestid,
  triggerButton,
  loadedFile,
  explanatoryText,
  containerProps,
}: FileFieldArgs): React.ReactNode {
  const generatedId = React.useId();
  const id = passedId ?? generatedId;
  const { classes } = useStyles();

  const [failedToLoad, setFailedToLoad] = useState(false);

  const [selectedFilename, setSelectedFilename] = useState<string | null>(null);

  useEffect(() => setSelectedFilename(loadedFile?.name ?? null), [loadedFile]);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    readFileAsBinaryString(file)
      .then((binaryString) => {
        setFailedToLoad(false);
        setSelectedFilename(file.name);
        onChange({ binaryString, file });
      })
      // reset value allows re-selection of last selected file
      .then(() => {
        event.target.value = "";
      })
      .catch((readError) => {
        setFailedToLoad(true);
        console.error("Failed to load file.", readError);
      });
  };

  const helperText = failedToLoad
    ? "Failed to load file. Please try again."
    : "";
  return (
    <>
      <input
        accept={accept}
        className={classes.input}
        id={id}
        name={name}
        onChange={handleChange}
        type="file"
        value={value}
        data-test-id={datatestid}
        disabled={disabled}
      />
      {triggerButton?.({ id }) ?? (
        <FormControl sx={{ width: "100%" }}>
          <InputBase
            type="file"
            inputProps={{
              accept,
            }}
             
            inputComponent={forwardRef(function FileInputTrigger(
              _,
              ref: React.ForwardedRef<HTMLLabelElement>,
            ) {
              return (
                <ButtonThatTriggersInvisibleInput
                  disabled={disabled}
                  buttonLabel={buttonLabel}
                  InputProps={InputProps}
                  id={id}
                  icon={icon}
                  ref={ref}
                  explanatoryText={explanatoryText}
                  containerProps={containerProps}
                />
              );
            })}
            error={failedToLoad}
            disabled={disabled}
            className={classes.visibleInput}
          />
          {failedToLoad && (
            <FormHelperText error={failedToLoad}>{helperText}</FormHelperText>
          )}
          {warningAlert && (
            <Alert severity="warning" className={classes.alert}>
              {warningAlert}
            </Alert>
          )}
        </FormControl>
      )}
      {showSelectedFilename && (
        <SelectedFileInfo
          error={error}
          loading={loading}
          selectedFilename={selectedFilename}
        />
      )}
    </>
  );
}

export default observer(FileField);
