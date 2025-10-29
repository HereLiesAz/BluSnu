# Blu Snu: Testing

This document describes the testing strategy for the Blu Snu project.

## Unit Tests

Unit tests are the foundation of the testing strategy. They are used to test individual components in isolation. Unit tests should be small, fast, and focused. They should not have any external dependencies, such as the network or the database.

The Blu Snu project uses JUnit and Mockito for unit testing. All new code should be accompanied by a comprehensive suite of unit tests.

## Integration Tests

Integration tests are used to test the interactions between different components. They are typically larger and slower than unit tests. Integration tests may have external dependencies, such as the network or the database.

The Blu Snu project uses a combination of real and mock objects for integration testing. All new features should be accompanied by a suite of integration tests.

## End-to-End Tests

End-to-end tests are used to test the entire application from start to finish. They are the largest and slowest type of test. End-to-end tests are used to verify that the application meets the user's requirements.

The Blu Snu project uses a combination of manual and automated end-to-end tests. All new features should be tested end-to-end before being released.

## Manual Testing

In addition to automated tests, the Blu Snu project also relies on manual testing. Manual testing is used to find bugs that are difficult to find with automated tests. Manual testing is also used to verify that the application is easy to use and meets the user's expectations.

All new features should be manually tested by at least two different people before being released.
