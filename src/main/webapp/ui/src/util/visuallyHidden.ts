/**
 * Hides content visually while keeping it available to screen readers. Taken from
 * https://www.a11yproject.com/posts/how-to-hide-content/. 1px rather than 0px because VoiceOver
 * will not announce an element with no dimensions.
 */
export const visuallyHidden = {
  position: "absolute",
  height: "1px",
  width: "1px",
  overflow: "hidden",
  whiteSpace: "nowrap",
  clip: "rect(1px, 1px, 1px, 1px)",
  clipPath: "inset(50%)",
} as const;
