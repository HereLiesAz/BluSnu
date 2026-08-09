# Blu Snu: Testing

This document describes the testing strategy for the Blu Snu project. It distinguishes
between what exists **today** and what the project **aspires** to. Please keep it honest:
update the "Current state" notes when coverage actually changes, rather than describing a
suite that does not exist yet.

## Unit Tests

Unit tests are the foundation of the testing strategy. They test individual components
(mostly ViewModels and pure logic helpers) in isolation, without network or database
dependencies. The project uses JUnit, Mockito, and mockito-kotlin.

**Current state:** A small handful of unit tests exist under `app/src/test/`, covering a
few ViewModels (e.g. `FindViewModel`, `ReportingViewModel`, `BluffsViewModel`,
`BrakToothViewModel`, `BleSpamViewModel`) and `AttackChainExecutor`, plus the default
`ExampleUnitTest` template. Coverage is far from comprehensive — most modules, data
classes, and managers have no unit tests at all.

**Goal:** New code should ship with unit tests. Aim to grow coverage incrementally,
prioritising pure logic (path-loss math, triangulation, report generation, chain
execution) that is cheap to test without Android framework mocking.

## Integration Tests

Integration tests would exercise the interactions between components (e.g. a ViewModel
driving a repository backed by an in-memory Room database).

**Current state:** There is **no** integration test suite. This section describes an
intended future direction, not existing coverage.

## Instrumented / End-to-End Tests

Instrumented tests run on a device or emulator and would verify UI flows and framework
integration end to end.

**Current state:** The only instrumented test is the default `ExampleInstrumentedTest`
(a package-name assertion) under `app/src/androidTest/`. There is **no** end-to-end suite.
Building out Compose UI tests and navigation flows is aspirational future work.

## Manual Testing

Manual testing is used to catch issues that automated tests miss and to sanity-check the
overall experience, especially for hardware- and root-dependent modules that cannot be
exercised in CI.

**Current state:** Manual testing is ad hoc and performed by the maintainer. The
aspiration is to have significant changes manually verified before release; there is no
formal multi-reviewer sign-off process in place today.
