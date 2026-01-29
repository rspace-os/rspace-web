import globals from "globals";
import { defineConfig } from "@eslint/config-helpers";
import js from "@eslint/js";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import jsxA11y from "eslint-plugin-jsx-a11y";
import prettier from "eslint-plugin-prettier";
import jsdoc from "eslint-plugin-jsdoc";
import testingLibrary from "eslint-plugin-testing-library";
import jestDom from "eslint-plugin-jest-dom";
import typescript from "@typescript-eslint/eslint-plugin";
import typescriptParser from "@typescript-eslint/parser";

export default defineConfig([
  // Global ignores
  {
    ignores: [
      "**/node_modules/",
      ".git/",
      "**/dist/",
      "**/build/",
      "**/playwright-report/",
      "**/playwright/.cache/",
      "**/test-results/",
    ],
  },
  // Base configuration for all JavaScript files
  {
    files: ["**/*.js", "**/*.jsx"],
    languageOptions: {
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
      ecmaVersion: 2024,
      sourceType: "module",
      globals: {
        ...globals.browser,
        ...globals.node,
        // Browser globals are included by default in flat config
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      "jsx-a11y": jsxA11y,
      prettier,
      jsdoc,
    },
    settings: {
      react: {
        version: "detect",
      },
    },
    rules: {
      ...js.configs.recommended.rules,
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.recommended.rules,
      ...prettier.configs.recommended.rules,

      // Custom rules (keep sorted)
      "array-callback-return": "error",
      "block-scoped-var": "warn",
      camelcase: "warn",
      "class-methods-use-this": "off",
      complexity: "warn",
      "default-param-last": "warn",
      "dot-notation": "warn",
      eqeqeq: "error",
      "guard-for-in": "error",
      "jsdoc/require-jsdoc": [
        "warn",
        {
          publicOnly: true,
          contexts: ["ExportDefaultDeclaration", "ExportNamedDeclaration"],
          require: { FunctionDeclaration: false },
          enableFixer: false,
        },
      ],
      "max-statements-per-line": ["warn", { max: 2 }],
      "no-alert": "error",
      "no-console": ["warn", { allow: ["info", "warn", "error"] }],
      "no-constant-binary-expression": "error",
      "no-duplicate-imports": "warn",
      "no-else-return": ["error", { allowElseIf: false }],
      "no-empty-pattern": "off",
      "no-eval": "error",
      "no-global-assign": "error",
      "no-implicit-coercion": "error",
      "no-implied-eval": "error",
      "no-invalid-this": "warn",
      "no-lonely-if": "warn",
      "no-multi-assign": "error",
      "no-nested-ternary": "warn",
      "no-octal-escape": "error",
      "no-prototype-builtins": "off",
      "no-return-assign": ["error", "always"],
      "no-return-await": "warn",
      "no-self-compare": "error",
      "no-shadow": "warn",
      "no-shadow-restricted-names": "error",
      "no-throw-literal": "warn",
      "no-undefined": "off",
      "no-unneeded-ternary": "warn",
      "no-unused-expressions": "warn",
      "no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "no-use-before-define": "warn",
      "no-useless-concat": "error",
      "no-useless-return": "warn",
      "no-var": "error",
      "no-void": "off",
      "object-shorthand": ["warn", "always"],
      "prefer-arrow-callback": "warn",
      "prefer-const": "warn",
      "prefer-promise-reject-errors": "warn",
      "prefer-rest-params": "warn",
      radix: ["warn", "always"],
      "require-await": "warn",
      yoda: ["warn", "never"],
      "react/prop-types": "error",
      // Remove when we have better tree-shaking
      "no-restricted-imports": [
        "error",
        {
          patterns: [{ regex: "^@fortawesome/free-[^/]+$" }],
        },
      ],
    },
  },

  // Test files configuration
  {
    files: [
      "**/__tests__/**/*.[jt]s?(x)",
      "**/__mocks__/**/*.[jt]s?(x)",
    ],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.vitest,
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      "jsx-a11y": jsxA11y,
      prettier,
      jsdoc,
      "testing-library": testingLibrary,
      "jest-dom": jestDom,
    },
    rules: {
      ...testingLibrary.configs.react.rules,
      ...jestDom.configs.recommended.rules,
    },
  },

  // TypeScript files configuration
  {
    files: ["**/*.ts", "**/*.tsx"],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: "./tsconfig.json",
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      "jsx-a11y": jsxA11y,
      prettier,
      jsdoc,
      "@typescript-eslint": typescript,
    },
    settings: {
      react: {
        version: "detect",
      },
    },
    rules: {
      ...typescript.configs.recommended.rules,
      ...typescript.configs["recommended-requiring-type-checking"].rules,
      "react/prop-types": "off",
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_" },
      ],
      // Override the base no-unused-vars for TypeScript files
      "no-unused-vars": "off",
    },
  },

  // TypeScript spec files configuration
  {
    files: ["**/*.spec.ts", "**/*.spec.tsx"],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: "./tsconfig.json",
      },
    },
    plugins: {
      "@typescript-eslint": typescript,
    },
    rules: {
      "@typescript-eslint/unbound-method": "off",
    },
  },
]);
