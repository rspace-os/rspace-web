import React, { useState } from "react";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
  ValidationResult,
} from "./ValidatingSubmitButton";
import { ThemeProvider } from "@mui/material/styles";
import theme from "../theme";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/rspace/other";
import Box from "@mui/material/Box";

/**
 * A simple example of how to use ValidatingSubmitButton
 */
export const SimpleExample = ({
  onClick = () => {},
}: {
  onClick: () => void;
}) => {
  const [validationResult, setValidationResult] =
    useState<ValidationResult>(IsValid());
  const [loading, setLoading] = useState(false);

  const handleClick = () => {
    onClick();
  };

  return (
    <ThemeProvider theme={theme}>
      <Box sx={{ padding: "20px" }}>
        <h3>Validating Submit Button</h3>
        <Box sx={{ marginBottom: "10px" }}>
          <button onClick={() => setValidationResult(IsValid())}>
            Set Valid
          </button>
          <button
            onClick={() => setValidationResult(IsInvalid("Validation failed."))}
          >
            Set Invalid
          </button>
          <button onClick={() => setLoading(!loading)}>
            Toggle Loading ({loading ? "Off" : "On"})
          </button>
        </Box>
        <ValidatingSubmitButton
          validationResult={validationResult}
          loading={loading}
          onClick={handleClick}
        >
          Submit
        </ValidatingSubmitButton>
      </Box>
    </ThemeProvider>
  );
};

/**
 * Example demonstrating the progress prop
 */
export const ProgressExample = ({
  onClick = () => {},
}: {
  onClick: () => void;
}) => {
  const [validationResult, setValidationResult] =
    useState<ValidationResult>(IsValid());
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState<number | undefined>(undefined);

  const handleClick = () => {
    onClick();
    setProgress(0);
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev === undefined || prev >= 100) {
          clearInterval(interval);
          return undefined;
        }
        return prev + 10;
      });
    }, 500);
  };

  return (
    <ThemeProvider theme={theme}>
      <Box sx={{ padding: "20px" }}>
        <h3>Validating Submit Button with Progress</h3>
        <Box sx={{ marginBottom: "10px" }}>
          <button onClick={() => setValidationResult(IsValid())}>
            Set Valid
          </button>
          <button
            onClick={() => setValidationResult(IsInvalid("Validation failed."))}
          >
            Set Invalid
          </button>
          <button onClick={() => setLoading(!loading)}>
            Toggle Loading ({loading ? "Off" : "On"})
          </button>
        </Box>
        <ValidatingSubmitButton
          validationResult={validationResult}
          loading={loading}
          progress={progress}
          onClick={handleClick}
        >
          Submit
        </ValidatingSubmitButton>
      </Box>
    </ThemeProvider>
  );
};

export const HighContrastExample = ({
  onClick = () => {},
}: {
  onClick: () => void;
}) => {
  const [validationResult, setValidationResult] =
    useState<ValidationResult>(IsValid());
  const [loading, setLoading] = useState(false);

  const handleClick = () => {
    onClick();
  };

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Box sx={{ padding: "20px" }}>
        <h3>Validating Submit Button (High Contrast)</h3>
        <Box sx={{ marginBottom: "10px" }}>
          <button onClick={() => setValidationResult(IsValid())}>
            Set Valid
          </button>
          <button
            onClick={() => setValidationResult(IsInvalid("Validation failed."))}
          >
            Set Invalid
          </button>
          <button onClick={() => setLoading(!loading)}>
            Toggle Loading ({loading ? "Off" : "On"})
          </button>
        </Box>
        <ValidatingSubmitButton
          validationResult={validationResult}
          loading={loading}
          onClick={handleClick}
        >
          Submit
        </ValidatingSubmitButton>
      </Box>
    </ThemeProvider>
  );
};
