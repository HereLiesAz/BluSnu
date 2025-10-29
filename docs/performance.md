# Blu Snu: Performance

This document discusses performance considerations and best practices for the Blu Snu application.

## General

*   **Avoid blocking the main thread.** This is the single most important rule for Android performance. All long-running operations, including Bluetooth scanning, network requests, and database access, should be performed on a background thread.
*   **Use appropriate data structures.** Choose the right data structure for the job. For example, use a `Map` for key-value pairs, a `List` for ordered data, and a `Set` for unordered data.
*   **Avoid unnecessary object creation.** Object creation can be expensive, especially in tight loops. Reuse objects whenever possible, and use primitive types instead of boxed types when appropriate.
*   **Use caching.** Cache data that is expensive to compute or fetch. This can include data from the network, the database, or the file system.

## Bluetooth

*   **Be mindful of battery life.** Bluetooth scanning and other radio-intensive operations can have a significant impact on battery life. Use these features judiciously and provide users with the ability to control them.
*   **Use the right scanning mode.** The Android Bluetooth APIs provide several different scanning modes, each with its own trade-offs in terms of performance and battery life. Choose the right scanning mode for your needs.
*   **Batch scan results.** If you are scanning for a large number of devices, it is more efficient to batch the scan results and deliver them to your application in a single callback.

## UI

*   **Use `RecyclerView` for long lists.** `RecyclerView` is a highly efficient way to display long lists of data. It recycles views as the user scrolls, which reduces memory usage and improves performance.
*   **Avoid deep view hierarchies.** Deep view hierarchies can be expensive to lay out and measure. Keep your view hierarchies as flat as possible.
*   **Use `ConstraintLayout` for complex layouts.** `ConstraintLayout` is a powerful and efficient way to create complex layouts. It can help you to avoid deep view hierarchies and improve the performance of your UI.
*   **Use vector drawables.** Vector drawables are smaller and more efficient than bitmap images. Use them whenever possible.
*   **Use ProGuard to shrink and obfuscate your code.** ProGuard can help you to reduce the size of your application and improve its performance.
