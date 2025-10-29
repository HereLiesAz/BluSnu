#!/system/bin/sh
#
# This script installs the BlueZ tools to /system/bin.
#
# The user must place the precompiled binaries for hcitool, btmgmt, etc.
# in the system/bin directory of this module.
#
MODDIR=${0%/*}

# Copy the BlueZ tools to /system/bin
cp $MODDIR/system/bin/* /system/bin/
