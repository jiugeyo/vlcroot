#!/system/bin/sh

MOUNT=/data/data/org.videolan.vlc/rootfs
umount -l "$MOUNT" 2>/dev/null
rmdir "$MOUNT" 2>/dev/null
exit 0
