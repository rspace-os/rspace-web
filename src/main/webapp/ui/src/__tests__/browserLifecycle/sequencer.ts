import { BaseSequencer, type TestSpecification } from "vitest/node";

const regressionFiles = ["/browserLifecycle/firstFile.spec.ts", "/browserLifecycle/nextFile.spec.ts"];

function priority(specification: TestSpecification): number {
  const index = regressionFiles.findIndex((file) => specification.moduleId.endsWith(file));
  return index === -1 ? regressionFiles.length : index;
}

export class BrowserTestSequencer extends BaseSequencer {
  async sort(files: TestSpecification[]): Promise<TestSpecification[]> {
    const sorted = await super.sort(files);
    // Keep the regression pair adjacent and ordered within each browser, even
    // when Vitest's cached timings would otherwise reverse their order.
    return sorted.sort((a, b) => priority(a) - priority(b));
  }
}
