# Advanced Kernel-Level Attacks

**Disclaimer:** The attacks described in this document are highly advanced and require a deep understanding of Bluetooth, as well as the ability to patch and compile a custom Linux kernel. These attacks are not for beginners and can easily brick your device if not performed correctly. The Blu Snu developers are not responsible for any damage to your device that may occur as a result of attempting these attacks.

## BLUFFS

The BLUFFS attack is a set of six attacks that break Bluetooth sessions' forward and future secrecy. This can allow an attacker to impersonate a legitimate device and establish a secure connection with a victim device without the victim's knowledge.

**Why a custom kernel is necessary:** The BLUFFS attack requires the ability to manipulate the Bluetooth session key derivation process. This is not possible with a standard Android kernel, as the necessary functions are not exposed to user-space applications.

**High-level summary of the required changes:**

*   Patch the kernel to expose the session key derivation functions to user-space.
*   Write a user-space application that uses these functions to manipulate the session key derivation process.

For more information, see: https://github.com/francozappa/bluffs

## BIAS

The Bluetooth Impersonation AttackS (BIAS) is a vulnerability that allows an attacker to impersonate a legitimate device during the Bluetooth pairing process. This can allow the attacker to establish a secure connection with a victim device without the victim's knowledge.

**Why a custom kernel is necessary:** The BIAS attack requires the ability to manipulate the Bluetooth pairing process. This is not possible with a standard Android kernel, as the necessary functions are not exposed to user-space applications.

**High-level summary of the required changes:**

*   Patch the kernel to expose the pairing functions to user-space.
*   Write a user-space application that uses these functions to manipulate the pairing process.

For more information, see: https://francozappa.github.io/about-bias/

## BLURtooth

The BLURtooth attack exploits vulnerabilities in the Cross-Transport Key Derivation (CTKD) of Bluetooth Classic and Bluetooth Low Energy. This can allow an attacker to overwrite the authentication keys and gain access to a victim's device.

**Why a custom kernel is necessary:** The BLURtooth attack requires the ability to manipulate the CTKD process. This is not possible with a standard Android kernel, as the necessary functions are not exposed to user-space applications.

**High-level summary of the required changes:**

*   Patch the kernel to expose the CTKD functions to user-space.
*   Write a user-space application that uses these functions to manipulate the CTKD process.

For more information, see: https://hexhive.epfl.ch/BLURtooth/
