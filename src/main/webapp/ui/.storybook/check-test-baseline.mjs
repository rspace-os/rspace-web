import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const baseline = JSON.parse(
  readFileSync(new URL("./known-test-failures.json", import.meta.url), "utf8"),
);
const temporaryDirectory = mkdtempSync(path.join(tmpdir(), "rspace-storybook-"));
const reportPath = path.join(temporaryDirectory, "report.json");
const runReportPath = path.join(temporaryDirectory, "run-report.json");
const reporterPath = path.join(temporaryDirectory, "run-reporter.mjs");
const require = createRequire(import.meta.url);
const vitestPackagePath = require.resolve("vitest/package.json");
const vitestPackage = JSON.parse(readFileSync(vitestPackagePath, "utf8"));
const vitestPath = path.resolve(path.dirname(vitestPackagePath), vitestPackage.bin.vitest);

try {
  writeFileSync(
    reporterPath,
    `import { writeFileSync } from "node:fs";
export default class StorybookRunReporter {
  onTestRunEnd(_testModules, unhandledErrors, reason) {
    writeFileSync(
      process.env.STORYBOOK_RUN_REPORT,
      JSON.stringify({ reason, unhandledErrors: unhandledErrors.map(({ name, message }) => ({ name, message })) }),
    );
  }
}
`,
  );
  const result = spawnSync(
    process.execPath,
    [
      vitestPath,
      "run",
      "--config",
      ".storybook/vitest.config.ts",
      "--reporter=json",
      `--reporter=${reporterPath}`,
      `--outputFile.json=${reportPath}`,
    ],
    {
      env: { ...process.env, STORYBOOK_RUN_REPORT: runReportPath },
      stdio: "inherit",
    },
  );

  if (result.error) throw result.error;
  if (!existsSync(reportPath)) {
    throw new Error(`Storybook tests exited with status ${result.status} without producing a report.`);
  }

  const report = JSON.parse(readFileSync(reportPath, "utf8"));
  const runReport = existsSync(runReportPath)
    ? JSON.parse(readFileSync(runReportPath, "utf8"))
    : { reason: "unknown", unhandledErrors: [{ message: "Vitest run reporter produced no result." }] };
  if (report.numTotalTests === 0) {
    throw new Error("Storybook did not run any tests.");
  }

  const suiteErrors = report.testResults
    .filter(
      (suite) =>
        suite.status === "failed" &&
        (suite.message.trim().length > 0 ||
          suite.assertionResults.every(({ status }) => status !== "failed")),
    )
    .map((suite) => path.relative(process.cwd(), suite.name).replaceAll(path.sep, "/"));
  const failures = report.testResults.flatMap((suite) =>
    suite.assertionResults
      .filter(({ status }) => status === "failed")
      .map((assertion) => ({
        id: `${path.relative(process.cwd(), suite.name).replaceAll(path.sep, "/")}::${assertion.fullName}`,
        messages: assertion.failureMessages,
      })),
  );
  const actual = failures.map(({ id }) => id).sort();
  const expected = [...baseline].sort();
  const unexpected = actual.filter((id) => !expected.includes(id));
  const resolved = expected.filter((id) => !actual.includes(id));
  const nonContrast = failures
    .filter(
      ({ messages }) =>
        messages.length === 0 ||
        messages.some((message) => {
          const ruleIds = [...message.matchAll(/\(([a-z][a-z0-9-]+)\)/g)].map(
            (match) => match[1],
          );
          return ruleIds.length === 0 || ruleIds.some((ruleId) => ruleId !== "color-contrast");
        }),
    )
    .map(({ id }) => id);

  if (unexpected.length > 0) {
    console.error("Unexpected Storybook failures:");
    console.error(unexpected.join("\n"));
  }
  if (resolved.length > 0) {
    console.error("Storybook failures no longer present; update the baseline:");
    console.error(resolved.join("\n"));
  }
  if (nonContrast.length > 0) {
    console.error("Baselined stories failed for reasons other than color contrast:");
    console.error(nonContrast.join("\n"));
  }
  if (suiteErrors.length > 0) {
    console.error("Storybook suites failed before producing failed assertions:");
    console.error(suiteErrors.join("\n"));
  }
  if (runReport.unhandledErrors.length > 0) {
    console.error("Unhandled Storybook test errors:");
    console.error(
      runReport.unhandledErrors
        .map(({ name, message }) => [name, message].filter(Boolean).join(": "))
        .join("\n"),
    );
  }

  if (
    unexpected.length > 0 ||
    resolved.length > 0 ||
    nonContrast.length > 0 ||
    suiteErrors.length > 0 ||
    runReport.unhandledErrors.length > 0
  ) {
    process.exitCode = 1;
  } else {
    console.log(
      `Storybook baseline matched: ${actual.length} known contrast failures and ${report.numPassedTests} passing tests.`,
    );
  }
} finally {
  rmSync(temporaryDirectory, { recursive: true, force: true });
}
