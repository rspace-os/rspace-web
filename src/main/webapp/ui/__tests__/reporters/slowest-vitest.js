class VitestSlowTestReporter {
  constructor() {
    this._slowTests = [];
  }

  onTestCaseResult(testCase) {
    const diagnostic = testCase.diagnostic?.();
    if (!diagnostic) {
      return;
    }

    const moduleId = testCase.module?.moduleId || "unknown";
    this._slowTests.push({
      duration: diagnostic.duration || 0,
      fullName: testCase.fullName,
      filePath: moduleId,
    });
  }

  onTestRunEnd() {
    if (!this._slowTests.length) {
      return;
    }

    console.log();
    this._slowTests.sort((a, b) => b.duration - a.duration);
    const rootPathRegex = new RegExp(`^${process.cwd()}`);
    const slowestTests = this._slowTests.slice(0, 10);
    const slowTestTime = this._slowTestTime(slowestTests);
    const allTestTime = this._allTestTime();
    const percentTime = allTestTime
      ? (slowTestTime / allTestTime) * 100
      : 0;

    console.log(
      `Top ${slowestTests.length} slowest examples (${
        slowTestTime / 1000
      } seconds, ${percentTime.toFixed(1)}% of total time):`
    );

    for (let i = 0; i < slowestTests.length; i++) {
      const duration = slowestTests[i].duration;
      const fullName = slowestTests[i].fullName;
      const filePath = slowestTests[i].filePath.replace(rootPathRegex, ".");

      console.log(`  ${fullName}`);
      console.log(`    ${duration / 1000} seconds ${filePath}`);
    }
    console.log();
  }

  _slowTestTime(slowestTests) {
    let slowTestTime = 0;
    for (let i = 0; i < slowestTests.length; i++) {
      slowTestTime += slowestTests[i].duration;
    }
    return slowTestTime;
  }

  _allTestTime() {
    let allTestTime = 0;
    for (let i = 0; i < this._slowTests.length; i++) {
      allTestTime += this._slowTests[i].duration;
    }
    return allTestTime;
  }
}

module.exports = VitestSlowTestReporter;
