import React, { useState } from "react";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
  ValidationResult,
} from "./ValidatingSubmitButton";

/**
 * A simple example of how to use ValidatingSubmitButton
 */
export const SimpleExample = ({
  onClick = () => {},
}: {
  onClick: () => void;
}) => {
  const [validationResult, setValidationResult] = useState<ValidationResult>(
    IsValid()
  );
  const [loading, setLoading] = useState(false);

  const handleClick = () => {
    onClick();
  };

  return (
    <div style={{ padding: "20px" }}>
      <h3>Validating Submit Button</h3>
      <div style={{ marginBottom: "10px" }}>
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
      </div>
      <ValidatingSubmitButton
        validationResult={validationResult}
        loading={loading}
        onClick={handleClick}
      >
        Submit
      </ValidatingSubmitButton>
    </div>
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
  const [validationResult, setValidationResult] = useState<ValidationResult>(
    IsValid()
  );
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
    <div style={{ padding: "20px" }}>
      <h3>Validating Submit Button with Progress</h3>
      <div style={{ marginBottom: "10px" }}>
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
      </div>
      <ValidatingSubmitButton
        validationResult={validationResult}
        loading={loading}
        progress={progress}
        onClick={handleClick}
      >
        Submit
      </ValidatingSubmitButton>
    </div>
  );
};
