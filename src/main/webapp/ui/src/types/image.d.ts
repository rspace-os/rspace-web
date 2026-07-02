declare module "*.jpg" {
  const src: string;
  export default src;
}

declare module "*.jpeg" {
  const src: string;
  export default src;
}

declare module "*.png" {
  const src: string;
  export default src;
}

declare module "*.gif" {
  const src: string;
  export default src;
}

declare module "*.svg" {
  const src: string;
  export default src;
}

// Vite `?url` asset imports (e.g. `import pdf from "./x.pdf?url"`) resolve to
// the served URL string. tsconfig restricts `types`, so vite/client's own
// declaration isn't loaded; declare it here.
declare module "*?url" {
  const src: string;
  export default src;
}
