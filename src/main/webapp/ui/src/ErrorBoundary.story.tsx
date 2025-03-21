import React from "react";

export function ErrorComponent(): React.ReactNode {
  throw new Error("Error");
}
