/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/.storybook/argTypeTemplates.ts
 * Upstream project license: MIT
 */
import type { ArgTypes } from "@storybook/react-vite";

// Type for individual argType configuration
type MuiArgType = ArgTypes[string];

// ═══════════════════════════════════════════════════════════════
// APPEARANCE CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiSizeArgType: MuiArgType = {
  control: "radio",
  options: ["small", "medium", "large"],
  description: "The size of the component.",
  table: {
    defaultValue: { summary: "medium" },
    category: "Appearance",
    type: { summary: '"small" | "medium" | "large"' },
  },
};

export const muiVariantArgType = (options: string[], defaultValue = options[0]): MuiArgType => ({
  control: "radio",
  options,
  description: "The variant to use.",
  table: {
    defaultValue: { summary: defaultValue },
    category: "Appearance",
    type: { summary: options.map((o) => `"${o}"`).join(" | ") },
  },
});

// ═══════════════════════════════════════════════════════════════
// STATE CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiDisabledArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component is disabled.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiRequiredArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the input is required.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiErrorArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component displays an error state.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiLoadingArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the loading indicator is shown.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiCheckedArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component is checked.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiSelectedArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component is selected.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

export const muiOpenArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component is shown.",
  table: {
    defaultValue: { summary: "false" },
    category: "State",
    type: { summary: "boolean" },
  },
};

// ═══════════════════════════════════════════════════════════════
// LAYOUT CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiFullWidthArgType: MuiArgType = {
  control: "boolean",
  description: "If true, the component will take up the full width of its container.",
  table: {
    defaultValue: { summary: "false" },
    category: "Layout",
    type: { summary: "boolean" },
  },
};

export const muiOrientationArgType: MuiArgType = {
  control: "radio",
  options: ["horizontal", "vertical"],
  description: "The component orientation.",
  table: {
    defaultValue: { summary: "horizontal" },
    category: "Layout",
    type: { summary: '"horizontal" | "vertical"' },
  },
};

export const muiPositionArgType = (
  options: string[] = ["fixed", "absolute", "sticky", "static", "relative"],
  defaultValue = "fixed",
): MuiArgType => ({
  control: "select",
  options,
  description: "The positioning type.",
  table: {
    defaultValue: { summary: defaultValue },
    category: "Layout",
    type: { summary: options.map((o) => `"${o}"`).join(" | ") },
  },
});

export const muiAnchorArgType: MuiArgType = {
  control: "select",
  options: ["left", "top", "right", "bottom"],
  description: "Side from which the drawer will appear.",
  table: {
    defaultValue: { summary: "left" },
    category: "Layout",
    type: { summary: '"left" | "top" | "right" | "bottom"' },
  },
};

export const muiPlacementArgType: MuiArgType = {
  control: "select",
  options: [
    "top",
    "top-start",
    "top-end",
    "bottom",
    "bottom-start",
    "bottom-end",
    "left",
    "left-start",
    "left-end",
    "right",
    "right-start",
    "right-end",
  ],
  description: "Tooltip placement.",
  table: {
    defaultValue: { summary: "bottom" },
    category: "Layout",
    type: { summary: "Placement" },
  },
};

// ═══════════════════════════════════════════════════════════════
// CONTENT CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiChildrenArgType: MuiArgType = {
  control: "text",
  description: "The content of the component.",
  table: {
    category: "Content",
    type: { summary: "ReactNode" },
  },
};

export const muiLabelArgType: MuiArgType = {
  control: "text",
  description: "The label content.",
  table: {
    category: "Content",
    type: { summary: "ReactNode" },
  },
};

// ═══════════════════════════════════════════════════════════════
// TYPOGRAPHY CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiTypographyVariantArgType: MuiArgType = {
  control: "select",
  options: [
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "subtitle1",
    "subtitle2",
    "body1",
    "body2",
    "button",
    "caption",
    "overline",
    "inherit",
  ],
  description: "Applies the theme typography styles.",
  table: {
    defaultValue: { summary: "body1" },
    category: "Typography",
    type: { summary: "Variant" },
  },
};

export const muiAlignArgType: MuiArgType = {
  control: "select",
  options: ["inherit", "left", "center", "right", "justify"],
  description: "Set the text-align on the component.",
  table: {
    defaultValue: { summary: "inherit" },
    category: "Typography",
    type: {
      summary: '"inherit" | "left" | "center" | "right" | "justify"',
    },
  },
};

// ═══════════════════════════════════════════════════════════════
// FEEDBACK CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiSeverityArgType: MuiArgType = {
  control: "select",
  options: ["success", "info", "warning", "error"],
  description: "The severity of the alert.",
  table: {
    defaultValue: { summary: "success" },
    category: "Appearance",
    type: { summary: '"success" | "info" | "warning" | "error"' },
  },
};

// ═══════════════════════════════════════════════════════════════
// NUMERIC CONTROLS
// ═══════════════════════════════════════════════════════════════

export const muiElevationArgType: MuiArgType = {
  control: { type: "range", min: 0, max: 24, step: 1 },
  description: "Shadow depth, corresponds to dp.",
  table: {
    defaultValue: { summary: "1" },
    category: "Appearance",
    type: { summary: "number" },
  },
};

export const muiMaxWidthArgType: MuiArgType = {
  control: "select",
  options: ["xs", "sm", "md", "lg", "xl", false],
  description: "Determine the max-width of the container.",
  table: {
    defaultValue: { summary: "lg" },
    category: "Layout",
    type: { summary: '"xs" | "sm" | "md" | "lg" | "xl" | false' },
  },
};

// ═══════════════════════════════════════════════════════════════
// HELPER FOR CUSTOM VARIANTS
// ═══════════════════════════════════════════════════════════════

export const createSelectArgType = (
  options: (string | boolean)[],
  defaultValue: string | boolean,
  description: string,
  category = "Appearance",
): MuiArgType => ({
  control: "select",
  options,
  description,
  table: {
    defaultValue: { summary: String(defaultValue) },
    category,
    type: { summary: options.map((o) => JSON.stringify(o)).join(" | ") },
  },
});

export const createRadioArgType = (
  options: string[],
  defaultValue: string,
  description: string,
  category = "Appearance",
): MuiArgType => ({
  control: "radio",
  options,
  description,
  table: {
    defaultValue: { summary: defaultValue },
    category,
    type: { summary: options.map((o) => `"${o}"`).join(" | ") },
  },
});

export const createBooleanArgType = (description: string, defaultValue = false, category = "State"): MuiArgType => ({
  control: "boolean",
  description,
  table: {
    defaultValue: { summary: String(defaultValue) },
    category,
    type: { summary: "boolean" },
  },
});

export const createNumberArgType = (
  description: string,
  defaultValue: number,
  min?: number,
  max?: number,
  category = "Appearance",
): MuiArgType => ({
  control: { type: "number", min, max },
  description,
  table: {
    defaultValue: { summary: String(defaultValue) },
    category,
    type: { summary: "number" },
  },
});
