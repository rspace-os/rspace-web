/// <reference types="@sa11y/vitest" />

/*
 * All SVG files should be treated by TypeScript as strings,
 * as the frontend build treats them as paths.
 */
declare module "*.svg" {
  const content: string;
  export default content;
}
