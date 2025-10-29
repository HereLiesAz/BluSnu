# Blu Snu: Common Mistakes and Anti-Patterns

This document lists common mistakes and anti-patterns to avoid when working on the Blu Snu project. Adhering to these guidelines will help to maintain the quality, consistency, and security of the codebase.

## General

*   **Don't ignore the ethical use mandate.** Blu Snu is a powerful tool that should only be used for legitimate security research and professional penetration testing. Always be mindful of the ethical implications of your work.
*   **Don't reinvent the wheel.** The Blu Snu project is built on a foundation of existing open-source tools and research. Before implementing a new feature, be sure to research existing solutions and leverage them whenever possible.
*   **Don't neglect documentation.** Every new feature should be accompanied by clear and concise documentation. This includes both user-facing documentation and technical documentation for other developers.
*   **Don't forget to test.** All new code should be accompanied by a comprehensive suite of tests. This includes unit tests, integration tests, and end-to-end tests.

## Android Development

*   **Don't block the main thread.** Bluetooth operations and other long-running tasks should always be performed on a background thread to avoid blocking the main thread and causing the application to become unresponsive.
*   **Don't neglect permissions.** The Blu Snu application requires a number of sensitive permissions to function correctly. Be sure to request these permissions in a clear and transparent way, and only when they are absolutely necessary.
*   **Don't forget about battery life.** Bluetooth scanning and other radio-intensive operations can have a significant impact on battery life. Be sure to use these features judiciously and provide users with the ability to control them.

## Security

*   **Don't trust user input.** All user input should be treated as untrusted and should be validated before being used. This includes data received from Bluetooth devices, as well as data entered by the user.
*   **Don't hardcode sensitive data.** Sensitive data, such as API keys and passwords, should never be hardcoded in the application. Instead, it should be stored securely and accessed through a secure API.
*   **Don't neglect error handling.** All error conditions should be handled gracefully. This includes errors that occur during Bluetooth operations, as well as errors that occur in other parts of the application.
