/*
 * All SVG files should be treated by TypeScript as strings,
 * as webpack processes them as paths.
 */
declare module "*.svg" {
  const content: string;
  export default content;
}
