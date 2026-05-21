package com.hereliesaz.blusnu.data

object HidKeyMap {

    // Modifier bit flags (byte 0 of keyboard report)
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04
    const val MOD_LEFT_GUI: Byte = 0x08
    const val MOD_RIGHT_CTRL: Byte = 0x10
    const val MOD_RIGHT_SHIFT: Byte = 0x20
    const val MOD_RIGHT_ALT: Byte = 0x40

    // Special key codes
    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESCAPE: Byte = 0x29
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_TAB: Byte = 0x2B
    const val KEY_SPACE: Byte = 0x2C
    const val KEY_CAPS_LOCK: Byte = 0x39
    const val KEY_RIGHT_ARROW: Byte = 0x4F
    const val KEY_LEFT_ARROW: Byte = 0x50
    const val KEY_DOWN_ARROW: Byte = 0x51
    const val KEY_UP_ARROW: Byte = 0x52
    const val KEY_DELETE: Byte = 0x4C

    // F-keys
    const val KEY_F1: Byte = 0x3A
    const val KEY_F2: Byte = 0x3B
    const val KEY_F3: Byte = 0x3C
    const val KEY_F4: Byte = 0x3D
    const val KEY_F5: Byte = 0x3E
    const val KEY_F6: Byte = 0x3F
    const val KEY_F7: Byte = 0x40
    const val KEY_F8: Byte = 0x41
    const val KEY_F9: Byte = 0x42
    const val KEY_F10: Byte = 0x43
    const val KEY_F11: Byte = 0x44
    const val KEY_F12: Byte = 0x45

    // Mouse button flags
    const val MOUSE_BUTTON_LEFT: Byte = 0x01
    const val MOUSE_BUTTON_RIGHT: Byte = 0x02
    const val MOUSE_BUTTON_MIDDLE: Byte = 0x04

    data class KeyMapping(val keyCode: Byte, val modifier: Byte = MOD_NONE)

    private val charMap: Map<Char, KeyMapping> = buildMap {
        // Lowercase a-z → HID 0x04–0x1D
        for (i in 0..25) {
            put('a' + i, KeyMapping((0x04 + i).toByte()))
        }
        // Uppercase A-Z → same codes with Shift
        for (i in 0..25) {
            put('A' + i, KeyMapping((0x04 + i).toByte(), MOD_LEFT_SHIFT))
        }
        // Digits 1-9 → HID 0x1E–0x26, 0 → 0x27
        for (i in 1..9) {
            put('0' + i, KeyMapping((0x1D + i).toByte()))
        }
        put('0', KeyMapping(0x27))

        // Special characters (unshifted)
        put(' ', KeyMapping(KEY_SPACE))
        put('\n', KeyMapping(KEY_ENTER))
        put('\t', KeyMapping(KEY_TAB))
        put('-', KeyMapping(0x2D))
        put('=', KeyMapping(0x2E))
        put('[', KeyMapping(0x2F))
        put(']', KeyMapping(0x30))
        put('\\', KeyMapping(0x31))
        put(';', KeyMapping(0x33))
        put('\'', KeyMapping(0x34))
        put('`', KeyMapping(0x35))
        put(',', KeyMapping(0x36))
        put('.', KeyMapping(0x37))
        put('/', KeyMapping(0x38))

        // Shifted special characters
        put('!', KeyMapping(0x1E, MOD_LEFT_SHIFT))
        put('@', KeyMapping(0x1F, MOD_LEFT_SHIFT))
        put('#', KeyMapping(0x20, MOD_LEFT_SHIFT))
        put('$', KeyMapping(0x21, MOD_LEFT_SHIFT))
        put('%', KeyMapping(0x22, MOD_LEFT_SHIFT))
        put('^', KeyMapping(0x23, MOD_LEFT_SHIFT))
        put('&', KeyMapping(0x24, MOD_LEFT_SHIFT))
        put('*', KeyMapping(0x25, MOD_LEFT_SHIFT))
        put('(', KeyMapping(0x26, MOD_LEFT_SHIFT))
        put(')', KeyMapping(0x27, MOD_LEFT_SHIFT))
        put('_', KeyMapping(0x2D, MOD_LEFT_SHIFT))
        put('+', KeyMapping(0x2E, MOD_LEFT_SHIFT))
        put('{', KeyMapping(0x2F, MOD_LEFT_SHIFT))
        put('}', KeyMapping(0x30, MOD_LEFT_SHIFT))
        put('|', KeyMapping(0x31, MOD_LEFT_SHIFT))
        put(':', KeyMapping(0x33, MOD_LEFT_SHIFT))
        put('"', KeyMapping(0x34, MOD_LEFT_SHIFT))
        put('~', KeyMapping(0x35, MOD_LEFT_SHIFT))
        put('<', KeyMapping(0x36, MOD_LEFT_SHIFT))
        put('>', KeyMapping(0x37, MOD_LEFT_SHIFT))
        put('?', KeyMapping(0x38, MOD_LEFT_SHIFT))
    }

    fun charToHid(char: Char): KeyMapping? = charMap[char]

    // Combined keyboard + mouse HID report descriptor
    val REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        // Keyboard
        0x05, 0x01,               // Usage Page (Generic Desktop)
        0x09, 0x06,               // Usage (Keyboard)
        0xA1.toByte(), 0x01,      // Collection (Application)
        0x85.toByte(), 0x01,      //   Report ID (1)
        0x05, 0x07,               //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0.toByte(),      //   Usage Minimum (Left Control)
        0x29, 0xE7.toByte(),      //   Usage Maximum (Right GUI)
        0x15, 0x00,               //   Logical Minimum (0)
        0x25, 0x01,               //   Logical Maximum (1)
        0x75, 0x01,               //   Report Size (1)
        0x95.toByte(), 0x08,      //   Report Count (8)
        0x81.toByte(), 0x02,      //   Input (Data, Variable, Absolute) — Modifiers
        0x95.toByte(), 0x01,      //   Report Count (1)
        0x75, 0x08,               //   Report Size (8)
        0x81.toByte(), 0x01,      //   Input (Constant) — Reserved
        0x95.toByte(), 0x06,      //   Report Count (6)
        0x75, 0x08,               //   Report Size (8)
        0x15, 0x00,               //   Logical Minimum (0)
        0x25, 0x65,               //   Logical Maximum (101)
        0x05, 0x07,               //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,               //   Usage Minimum (0)
        0x29, 0x65,               //   Usage Maximum (101)
        0x81.toByte(), 0x00,      //   Input (Data, Array) — Key codes
        0xC0.toByte(),            // End Collection

        // Mouse
        0x05, 0x01,               // Usage Page (Generic Desktop)
        0x09, 0x02,               // Usage (Mouse)
        0xA1.toByte(), 0x01,      // Collection (Application)
        0x85.toByte(), 0x02,      //   Report ID (2)
        0x09, 0x01,               //   Usage (Pointer)
        0xA1.toByte(), 0x00,      //   Collection (Physical)
        0x05, 0x09,               //     Usage Page (Button)
        0x19, 0x01,               //     Usage Minimum (1)
        0x29, 0x03,               //     Usage Maximum (3)
        0x15, 0x00,               //     Logical Minimum (0)
        0x25, 0x01,               //     Logical Maximum (1)
        0x95.toByte(), 0x03,      //     Report Count (3)
        0x75, 0x01,               //     Report Size (1)
        0x81.toByte(), 0x02,      //     Input (Data, Variable, Absolute) — Buttons
        0x95.toByte(), 0x01,      //     Report Count (1)
        0x75, 0x05,               //     Report Size (5)
        0x81.toByte(), 0x01,      //     Input (Constant) — Padding
        0x05, 0x01,               //     Usage Page (Generic Desktop)
        0x09, 0x30,               //     Usage (X)
        0x09, 0x31,               //     Usage (Y)
        0x09, 0x38,               //     Usage (Wheel)
        0x15, 0x81.toByte(),      //     Logical Minimum (-127)
        0x25, 0x7F,               //     Logical Maximum (127)
        0x75, 0x08,               //     Report Size (8)
        0x95.toByte(), 0x03,      //     Report Count (3)
        0x81.toByte(), 0x06,      //     Input (Data, Variable, Relative) — X, Y, Wheel
        0xC0.toByte(),            //   End Collection
        0xC0.toByte()             // End Collection
    )

    // Keyboard-only report descriptor (for BLE HID which uses separate services)
    val KEYBOARD_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05, 0x01,               // Usage Page (Generic Desktop)
        0x09, 0x06,               // Usage (Keyboard)
        0xA1.toByte(), 0x01,      // Collection (Application)
        0x05, 0x07,               //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0.toByte(),      //   Usage Minimum (Left Control)
        0x29, 0xE7.toByte(),      //   Usage Maximum (Right GUI)
        0x15, 0x00,               //   Logical Minimum (0)
        0x25, 0x01,               //   Logical Maximum (1)
        0x75, 0x01,               //   Report Size (1)
        0x95.toByte(), 0x08,      //   Report Count (8)
        0x81.toByte(), 0x02,      //   Input (Data, Variable, Absolute) — Modifiers
        0x95.toByte(), 0x01,      //   Report Count (1)
        0x75, 0x08,               //   Report Size (8)
        0x81.toByte(), 0x01,      //   Input (Constant) — Reserved
        0x95.toByte(), 0x06,      //   Report Count (6)
        0x75, 0x08,               //   Report Size (8)
        0x15, 0x00,               //   Logical Minimum (0)
        0x25, 0x65,               //   Logical Maximum (101)
        0x05, 0x07,               //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,               //   Usage Minimum (0)
        0x29, 0x65,               //   Usage Maximum (101)
        0x81.toByte(), 0x00,      //   Input (Data, Array) — Key codes
        0xC0.toByte()             // End Collection
    )

    val MOUSE_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05, 0x01,               // Usage Page (Generic Desktop)
        0x09, 0x02,               // Usage (Mouse)
        0xA1.toByte(), 0x01,      // Collection (Application)
        0x09, 0x01,               //   Usage (Pointer)
        0xA1.toByte(), 0x00,      //   Collection (Physical)
        0x05, 0x09,               //     Usage Page (Button)
        0x19, 0x01,               //     Usage Minimum (1)
        0x29, 0x03,               //     Usage Maximum (3)
        0x15, 0x00,               //     Logical Minimum (0)
        0x25, 0x01,               //     Logical Maximum (1)
        0x95.toByte(), 0x03,      //     Report Count (3)
        0x75, 0x01,               //     Report Size (1)
        0x81.toByte(), 0x02,      //     Input (Data, Variable, Absolute) — Buttons
        0x95.toByte(), 0x01,      //     Report Count (1)
        0x75, 0x05,               //     Report Size (5)
        0x81.toByte(), 0x01,      //     Input (Constant) — Padding
        0x05, 0x01,               //     Usage Page (Generic Desktop)
        0x09, 0x30,               //     Usage (X)
        0x09, 0x31,               //     Usage (Y)
        0x09, 0x38,               //     Usage (Wheel)
        0x15, 0x81.toByte(),      //     Logical Minimum (-127)
        0x25, 0x7F,               //     Logical Maximum (127)
        0x75, 0x08,               //     Report Size (8)
        0x95.toByte(), 0x03,      //     Report Count (3)
        0x81.toByte(), 0x06,      //     Input (Data, Variable, Relative) — X, Y, Wheel
        0xC0.toByte(),            //   End Collection
        0xC0.toByte()             // End Collection
    )
}
