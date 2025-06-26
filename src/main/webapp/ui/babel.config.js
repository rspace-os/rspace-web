const test = process.env.NODE_ENV === "test";

module.exports = {
  presets: [
    [
      "@babel/preset-env",
      {
        targets: {
          node: "current",
        },
      },
    ],
    "@babel/preset-react",
  ],
  plugins: [
    [
      "@babel/plugin-proposal-decorators",
      {
        legacy: true,
      },
    ],
    [
      "@babel/plugin-proposal-class-properties",
      {
        loose: false,
      },
    ],
    [
      "@babel/plugin-transform-runtime",
      {
        regenerator: true,
      },
    ],
    ["babel-plugin-syntax-hermes-parser"],
    ...(test ? ["babel-plugin-transform-import-meta"] : []),
  ],
};
