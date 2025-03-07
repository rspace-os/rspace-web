import React from "react";

export default function ErrorComponent(): React.ReactNode {
  throw new Error("Error");
}
