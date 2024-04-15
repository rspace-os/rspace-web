//@flow

import React, {
  type Node,
  type ComponentType,
  type ElementRef,
  type ElementConfig,
  useState,
  useEffect,
  forwardRef,
} from "react";
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

type ButtonThatTriggersInvisibleInputArgs = {|
  buttonLabel: string,
  InputProps: {
    startAdornment?: Node,
    endAdornment?: Node,
  },
  disabled?: boolean,
  id: string,
  icon: Node,
  explanatoryText?: string,
  adornmentWrapping?: string,
  containerProps?: ElementConfig<typeof Grid>,
  itemProps?: ElementConfig<typeof Grid>,
|};

const ButtonThatTriggersInvisibleInput = forwardRef<
  ButtonThatTriggersInvisibleInputArgs,
  ElementRef<"label">
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
      itemProps,
    }: ButtonThatTriggersInvisibleInputArgs,
    ref
  ) => (
    <Grid container spacing={1} {...containerProps}>
      {InputProps.startAdornment}
      <Grid item flexGrow={1} {...itemProps}>
        <label htmlFor={id} ref={ref}>
          {explanatoryText ? (
            <BigIconButton
              icon={icon}
              label={buttonLabel}
              explanatoryText={explanatoryText}
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
  )
);

ButtonThatTriggersInvisibleInput.displayName =
  "ButtonThatTriggersInvisibleInput";

export type FileFieldArgs = {|
  // required
  accept: string,
  onChange: ({
    binaryString: string,
    file: File,
  }) => void,

  // optional
  id?: string,
  InputProps?: {
    startAdornment?: Node,
    endAdornment?: Node,
  },
  buttonLabel?: string,
  datatestid?: string,
  disabled?: boolean,
  error?: boolean,
  icon?: Node,
  loadedFile?: ?File,
  loading?: boolean,
  name?: string,
  showSelectedFilename?: boolean,
  triggerButton?: ({| id: string |}) => Node,
  value?: string,
  warningAlert?: string,
  explanatoryText?: string,
  containerProps?: ElementConfig<typeof Grid>,
  itemProps?: ElementConfig<typeof Grid>,
|};

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
  itemProps,
}: FileFieldArgs): Node {
  const generatedId = React.useId();
  const id = passedId ?? generatedId;
  const { classes } = useStyles();

  const [failedToLoad, setFailedToLoad] = useState(false);

  const [selectedFilename, setSelectedFilename] = useState<?string>(null);

  useEffect(() => setSelectedFilename(loadedFile?.name), [loadedFile]);

  const handleChange = (event: {
    target: { files: Array<File>, value: string, ... },
    ...
  }) => {
    const file = event.target.files[0];
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
        <FormControl>
          <InputBase
            type="file"
            inputProps={{
              accept,
            }}
            //eslint-disable-next-line
            inputComponent={forwardRef((_, ref) => (
              <ButtonThatTriggersInvisibleInput
                disabled={disabled}
                buttonLabel={buttonLabel}
                InputProps={InputProps}
                id={id}
                icon={icon}
                ref={ref}
                explanatoryText={explanatoryText}
                containerProps={containerProps}
                itemProps={itemProps}
              />
            ))}
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

export default (observer(FileField): ComponentType<FileFieldArgs>);
