package com.researchspace.build;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * Stands for the test database that test tasks share. Registered with {@code maxParallelUsages = 1}
 * so that tasks using it run one at a time, as Surefire executions do.
 */
public abstract class TestDatabase implements BuildService<BuildServiceParameters.None> {}
