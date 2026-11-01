#!/system/bin/sh

MODDIR=${0%/*}
LOG=/data/local/tmp/vlcrootfs.log
VLC=/data/data/org.videolan.vlc
MOUNT=$VLC/rootfs

echo "=== start $(date) ===" >> "$LOG"

# 等 VLC 的数据目录出现（首次开机/刚安装时可能还没有）
i=0
while [ ! -d "$VLC" ] && [ $i -lt 120 ]; do
  sleep 1
  i=$((i+1))
done
if [ ! -d "$VLC" ]; then
  echo "VLC data dir not found, abort" >> "$LOG"
  exit 1
fi

VUID=$(stat -c %u "$VLC")
VGID=$(stat -c %g "$VLC")
echo "uid=$VUID gid=$VGID" >> "$LOG"

mkdir -p "$MOUNT"
chown "$VUID:$VGID" "$MOUNT" 2>>"$LOG"
chmod 700 "$MOUNT" 2>>"$LOG"

# 清掉上次残留（如果还有）
umount -l "$MOUNT" 2>/dev/null
rmdir "$MOUNT" 2>/dev/null
mkdir -p "$MOUNT"
chown "$VUID:$VGID" "$MOUNT" 2>>"$LOG"
chmod 700 "$MOUNT" 2>>"$LOG"

"$MODDIR/rootfsd" -mount "$MOUNT" -source /data -uid "$VUID" -gid "$VGID" >> "$LOG" 2>&1 &
echo "rootfsd pid=$!" >> "$LOG"
