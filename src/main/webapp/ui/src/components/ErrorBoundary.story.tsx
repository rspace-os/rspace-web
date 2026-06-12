// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";

export function ErrorComponent(): React.ReactNode {
  throw new Error("Error");
}
