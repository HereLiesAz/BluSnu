# Blu Snu: Authentication and Authorization

This document details the authentication and authorization mechanisms for the Blu Snu application, including the ethical use mandate and permission requirements.

## Ethical Use Mandate

The development and distribution of a powerful offensive security tool like Blu Snu carry a significant ethical responsibility. The tool must be designed and positioned explicitly for legitimate security research and professional penetration testing, and must incorporate features that discourage malicious use.

*   **Mandatory Disclaimer:** Upon first launch, the application will display a prominent, non-skippable disclaimer. This statement will clearly articulate that the tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which they have received explicit, written authorization. It will state unequivocally that using the tool for unauthorized access or malicious activity is illegal and that the developers assume no liability for its misuse.
*   **Professional Reporting Engine:** To reinforce its role as a professional assessment tool, Blu Snu will feature a robust reporting engine. After completing an assessment, whether using individual modules or complex attack chains, the operator can generate a detailed report. This report, exportable in formats like PDF and Markdown, will serve as official documentation for a penetration testing engagement. It will automatically log:
    *   A timeline of all actions performed.
    *   The specific targets (MAC addresses and device names) of each action.
    *   The configuration parameters used for each attack module.
    *   The results and logs of each operation (e.g., "Connection successful," "Vulnerability CVE-2023-45866 detected," "Data exfiltrated from phonebook").
*   **Transparency and No Obfuscation:** The tool's purpose will never be misrepresented. It will be marketed and described openly as an offensive security and penetration testing framework. This transparent approach aligns with the ethos of the open-source security research community, from which many of the integrated tools and concepts originate.

## Android Permissions and Requirements

To function correctly, the Blu Snu application will require a specific set of permissions to be declared in its Android manifest file. The application must provide clear and transparent justifications to the user for each requested permission, particularly those that are sensitive or have privacy implications.

*   **Core Bluetooth Permissions (Android 12 and higher):**
    *   `BLUETOOTH_SCAN`: Required to discover nearby Bluetooth devices. This is a runtime permission that must be explicitly granted by the user.
    *   `BLUETOOTH_CONNECT`: Required to initiate connections to, and communicate with, paired Bluetooth devices. This is also a runtime permission.
    *   `BLUETOOTH_ADVERTISE`: Required for modules that need to broadcast as a BLE peripheral, such as the Btlejuice MitM framework. This is a runtime permission.
*   **Location Permissions:**
    *   `ACCESS_FINE_LOCATION`: On Android versions prior to 12, this permission is mandatory for any app that performs Bluetooth scanning. For Android 12 and higher, it is required for the RSSI Geolocation and Tracking module. For all other functions, the application will set the `android:usesPermissionFlags="neverForLocation"` attribute in the `BLUETOOTH_SCAN` permission declaration. This is a strong assertion to the system and the user that the scan results are not being used to determine the user's physical location, which can help build user trust and is a best practice when location is not a core function.
    *   `ACCESS_BACKGROUND_LOCATION`: This highly sensitive permission will only be requested if the user explicitly enables a feature that requires continuous monitoring or scanning while the app is not in the foreground, such as a persistent proximity alert.
*   **Other Permissions:**
    *   `INTERNET`: Required for downloading updates to the internal vulnerability and device fingerprinting databases.
    *   `WRITE_EXTERNAL_STORAGE`: Required for saving session data, captured packets (PCAP files), and generated penetration test reports.
