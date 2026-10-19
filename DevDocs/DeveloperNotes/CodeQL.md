# CodeQL code scanning

`.github/workflows/codeql.yml` runs GitHub CodeQL on every pull request to `main` and every push to
`main`. Results appear on the pull request as the `CodeQL` check and in the
repository's Security tab under Code scanning.

## What is scanned

Three analyses run in parallel: Java (`build-mode: none`, so nothing is compiled),
JavaScript/TypeScript, and GitHub Actions workflows. Paths are set in
`.github/codeql/codeql-config.yml`. Java covers the whole tree. JavaScript is limited to
`src/main/webapp/ui/src`; the legacy JavaScript under `src/main/webapp/scripts` is mostly
vendored and is excluded until its first-party files are triaged.

Pull requests and pushes run CodeQL's default query suite.

## When the check fails

Code scanning fails a pull request only for alerts that the pull request introduces. Alerts that
already exist on `main` show in the Security tab and do not block anyone.

If your change introduces an alert, read the alert's data-flow path on the pull request. Fix it
when the flow is real. When it is a false positive, ask a repository admin to dismiss it in the
Security tab with a reason; do not suppress it in code.
