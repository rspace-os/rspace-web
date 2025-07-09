import Result from "../util/result";
import SubmitSpinnerButton from "./SubmitSpinnerButton";
import React from "react";
import Popover from "@mui/material/Popover";
import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import type { Progress } from "../util/progress";
import Fade from "@mui/material/Fade";

export type ValidationResult = Result<null>;

/*
 * These two functions have leading capital 'I's as they are analogous to the
 * built-in object `Symbol` in that they are very simple immutable identifiers
 * to abstract concepts. In this case, to the notion of whether a computation
 * has determined a piece of data to be in a valid or invalid state and thus
 * whether the user is permitted to proceed or is to be presented with
 * warnings that they must first resolve.
 */
export const IsValid = (): ValidationResult => Result.Ok(null);
export const IsInvalid = (reason: string): ValidationResult =>
  Result.Error([new Error(reason)]);

export const allAreValid = (
  v: ReadonlyArray<ValidationResult>,
): ValidationResult => Result.all(...v).map(() => null);

type ValidatingSubmitButtonArgs = {
  children: React.ReactNode;
  loading: boolean;
  validationResult: ValidationResult;
  onClick: (event: React.MouseEvent<HTMLButtonElement>) => void;
  progress?: Progress;
};

const StyledPopover = styled(Popover)(() => ({
  "& > .MuiPaper-root": {
    padding: "2px",
    transitionDelay: "150ms !important",
  },
}));

const StyledButton = styled(
  ({
    animating: _animating,
    ...rest
  }: {
    animating: boolean;
  } & Omit<React.ComponentProps<typeof SubmitSpinnerButton>, "animating">) => (
    <SubmitSpinnerButton {...rest} />
  ),
)(({ animating }) => ({
  "@keyframes wiggle": {
    "0%, 7%": {
      transform: "translateX(0)",
    },
    "15%": {
      transform: "translateX(-8px)",
    },
    "20%": {
      transform: "translateX(5px)",
    },
    "25%": {
      transform: "translateX(-5px)",
    },
    "30%": {
      transform: "translateX(3px)",
    },
    "35%": {
      transform: "translateX(-2px)",
    },
    "40%, 100%": {
      transform: "translateX(0)",
    },
  },
  ...(animating &&
  !window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? {
        animation: "wiggle 1s linear 1",
      }
    : {}),
}));

export default function ValidatingSubmitButton({
  children,
  loading,
  validationResult,
  onClick,
  progress,
}: ValidatingSubmitButtonArgs): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<Element | null>(null);
  const [playAnimation, setPlayAnimation] = React.useState(false);

  return (
    <>
      <StyledButton
        label={children}
        disabled={loading}
        loading={loading}
        /*
         * By using type="submit", any <form> element that wraps this button
         * will delegate to this button's onClick whenever the user submits the
         * form, including when they press enter inside of a textfield. As
         * such, there is no need to implement the submit handler twice, the
         * <form>'s one can be left undefined. All of this results in the form
         * submission being more keyboard friendly and thus more accessible to
         * users who struggle with precise pointer interactions.
         */
        type="submit"
        progress={progress}
        onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
          e.preventDefault();
          setPlayAnimation(false);
          if (validationResult.isOk) return onClick(e);
          setAnchorEl(e.currentTarget);
          setPlayAnimation(true);
          setTimeout(() => {
            setPlayAnimation(false);
          }, 1000);
        }}
        animating={playAnimation}
      />
      <StyledPopover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        anchorOrigin={{
          vertical: "top",
          horizontal: "center",
        }}
        transformOrigin={{
          vertical: 60,
          horizontal: "center",
        }}
        transitionDuration={300}
        onClose={() => setAnchorEl(null)}
        PaperProps={{
          role: "dialog",
        }}
      >
        <Stack spacing={0.25}>
          {validationResult.orElseGet((errors) =>
            errors.map((error, i) => (
              <Fade in={true} key={i} timeout={200}>
                <Alert
                  severity="warning"
                  elevation={0}
                  aria-label="Warning"
                  sx={{
                    transitionDelay: `${
                      (errors.length - i) * 0.04 + 0.2
                    }s !important`,
                  }}
                >
                  {error.message}
                </Alert>
              </Fade>
            )),
          )}
        </Stack>
      </StyledPopover>
    </>
  );
}
