import PublishOutlinedIcon from "@mui/icons-material/PublishOutlined";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import Grid from "@mui/material/Grid";
import InputBase from "@mui/material/InputBase";
import { observer } from "mobx-react-lite";
import React, { forwardRef, useEffect, useState } from "react";

import BigIconButton from "../BigIconButton";
import SelectedFileInfo from "./SelectedFileInfo";

type FileFieldSlotProps = {
  input?: {
    startAdornment?: React.ReactNode;
    endAdornment?: React.ReactNode;
  };
};

type ButtonThatTriggersInvisibleInputArgs = {
  buttonLabel: string;
  slotProps?: FileFieldSlotProps;
  disabled?: boolean;
  id: string;
  icon: React.ReactNode;
  explanatoryText?: string;
  containerProps?: React.ComponentProps<typeof Grid>;
};

const ButtonThatTriggersInvisibleInput = forwardRef<React.ElementRef<"label">, ButtonThatTriggersInvisibleInputArgs>(
  (
    {
      buttonLabel,
      slotProps,
      disabled,
      id,
      icon,
      explanatoryText,
      containerProps,
    }: ButtonThatTriggersInvisibleInputArgs,
    ref,
  ) => (
    <Grid container spacing={1} {...containerProps}>
      {slotProps?.input?.startAdornment}
      <Grid sx={{ flexGrow: 1 }}>
        <label htmlFor={id} ref={ref}>
          {/* These buttons are rendered as HTMLSpanElements so that
           * tapping them results in the click event bubbling up to the
           * HTMLLableElement above, which in turn triggers the
           * HTMLInputElement with type "file" that is rendered in this
           * file, below.
           */}
          {explanatoryText ? (
            <BigIconButton icon={icon} label={buttonLabel} explanatoryText={explanatoryText} component="span" />
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
      {slotProps?.input?.endAdornment}
    </Grid>
  ),
);

ButtonThatTriggersInvisibleInput.displayName = "ButtonThatTriggersInvisibleInput";

export type FileFieldArgs = {
  // required
  accept: string;
  onChange: (event: { binaryString: string; file: File }) => void;

  // optional
  id?: string;
  buttonLabel?: string;
  "data-test-id"?: string;
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
  slotProps?: FileFieldSlotProps;

  /*
   * This overrides the default button that triggers the opening of the
   * operating system's file picker. If it is set, then some of the props
   * listed above are ignored:
   *   - slotProps
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
  id: passedId,
  buttonLabel = "Upload",
  icon = <PublishOutlinedIcon />,
  warningAlert = "",
  showSelectedFilename = false,
  loading = false,
  error = false,
  "data-test-id": dataTestId,
  triggerButton,
  loadedFile,
  explanatoryText,
  containerProps,
  slotProps,
}: FileFieldArgs): React.ReactNode {
  const generatedId = React.useId();
  const id = passedId ?? generatedId;

  const [failedToLoad, setFailedToLoad] = useState(false);

  const [selectedFilename, setSelectedFilename] = useState<string | null>(null);

  useEffect(() => setSelectedFilename(loadedFile?.name ?? null), [loadedFile]);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    Promise.resolve(file.arrayBuffer())
      .then((buf) => Array.from(new Uint8Array(buf), (b) => String.fromCharCode(b)).join(""))
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

  const helperText = failedToLoad ? "Failed to load file. Please try again." : "";
  return (
    <>
      <input
        accept={accept}
        style={{ display: "none" }}
        id={id}
        name={name}
        onChange={handleChange}
        type="file"
        value={value}
        data-test-id={dataTestId}
        disabled={disabled}
      />
      {triggerButton?.({ id }) ?? (
        <FormControl sx={{ width: "100%" }}>
          <InputBase
            type="file"
            slotProps={{
              input: {
                accept,
              },
            }}
            inputComponent={forwardRef(function FileInputTrigger(_, ref: React.ForwardedRef<HTMLLabelElement>) {
              return (
                <ButtonThatTriggersInvisibleInput
                  disabled={disabled}
                  buttonLabel={buttonLabel}
                  slotProps={slotProps}
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
            sx={{
              flexWrap: "wrap",
              justifyContent: "center",
            }}
          />
          {failedToLoad && <FormHelperText error={failedToLoad}>{helperText}</FormHelperText>}
          {warningAlert && (
            <Alert severity="warning" sx={{ mt: 1 }}>
              {warningAlert}
            </Alert>
          )}
        </FormControl>
      )}
      {showSelectedFilename && <SelectedFileInfo error={error} loading={loading} selectedFilename={selectedFilename} />}
    </>
  );
}

export default observer(FileField);
