//@flow

/*
 * This is a wrapper around getting `import.meta`, that can be mocked in jest
 * scripts, see ../../__mocks__/setupFiles/meta_import.js. For more information
 * on `import.meta`, see mdn
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/import.meta
 */
export const importMeta = (): Import$Meta => import.meta;
