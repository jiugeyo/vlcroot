package main

import (
	"context"
	"flag"
	"io"
	"log"
	"os"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/hanwen/go-fuse/v2/fs"
	"github.com/hanwen/go-fuse/v2/fuse"
)

type node struct {
	fs.Inode
	src string
	uid uint32
	gid uint32
}

func inodeOf(st os.FileInfo) uint64 {
	if s, ok := st.Sys().(*syscall.Stat_t); ok {
		return s.Ino
	}
	return uint64(st.ModTime().UnixNano())
}

func fillAttr(a *fuse.Attr, st os.FileInfo, uid, gid uint32) {
	mode := uint32(st.Mode().Perm())
	switch {
	case st.IsDir():
		mode |= syscall.S_IFDIR
	case st.Mode()&os.ModeSymlink != 0:
		mode |= syscall.S_IFLNK
	default:
		mode |= syscall.S_IFREG
	}
	a.Mode = mode
	a.Size = uint64(st.Size())
	a.Ino = inodeOf(st)
	a.Uid = uid
	a.Gid = gid
	a.Nlink = 1
	t := st.ModTime()
	a.Mtime = uint64(t.Unix())
	a.Mtimensec = uint32(t.Nanosecond())
	a.Atime = uint64(t.Unix())
	a.Atimensec = uint32(t.Nanosecond())
	a.Ctime = uint64(t.Unix())
	a.Ctimensec = uint32(t.Nanosecond())
}

func (n *node) Getattr(ctx context.Context, fh fs.FileHandle, out *fuse.AttrOut) syscall.Errno {
	st, err := os.Lstat(n.src)
	if err != nil {
		return fs.ToErrno(err)
	}
	fillAttr(&out.Attr, st, n.uid, n.gid)
	return 0
}

func (n *node) Lookup(ctx context.Context, name string, out *fuse.EntryOut) (*fs.Inode, syscall.Errno) {
	if name == "" || name == "." || name == ".." || strings.ContainsRune(name, '/') {
		return nil, syscall.EINVAL
	}
	p := filepath.Join(n.src, name)
	st, err := os.Lstat(p)
	if err != nil {
		return nil, fs.ToErrno(err)
	}
	// 不跟随/暴露符号链接，避免跳出 /data 白名单
	if st.Mode()&os.ModeSymlink != 0 {
		return nil, syscall.EACCES
	}
	fillAttr(&out.Attr, st, n.uid, n.gid)
	child := &node{src: p, uid: n.uid, gid: n.gid}
	return n.NewInode(ctx, child, fs.StableAttr{Mode: out.Attr.Mode, Ino: out.Attr.Ino}), 0
}

func (n *node) Readdir(ctx context.Context) (fs.DirStream, syscall.Errno) {
	entries, err := os.ReadDir(n.src)
	if err != nil {
		return nil, fs.ToErrno(err)
	}
	list := make([]fuse.DirEntry, 0, len(entries))
	for _, e := range entries {
		info, err := e.Info()
		if err != nil {
			continue
		}
		mode := uint32(info.Mode().Perm())
		if info.IsDir() {
			mode |= syscall.S_IFDIR
		} else {
			mode |= syscall.S_IFREG
		}
		list = append(list, fuse.DirEntry{Name: e.Name(), Mode: mode, Ino: inodeOf(info)})
	}
	return fs.NewListDirStream(list), 0
}

type handle struct {
	*os.File
}

func (n *node) Open(ctx context.Context, flags uint32) (fs.FileHandle, uint32, syscall.Errno) {
	if flags&uint32(syscall.O_WRONLY|syscall.O_RDWR|syscall.O_CREAT|syscall.O_TRUNC) != 0 {
		return nil, 0, syscall.EROFS
	}
	f, err := os.Open(n.src)
	if err != nil {
		return nil, 0, fs.ToErrno(err)
	}
	return &handle{f}, fuse.FOPEN_KEEP_CACHE, 0
}

func (h *handle) Read(ctx context.Context, dest []byte, off int64) (fuse.ReadResult, syscall.Errno) {
	n, err := h.ReadAt(dest, off)
	if err != nil && err != io.EOF {
		return nil, fs.ToErrno(err)
	}
	return fuse.ReadResultData(dest[:n]), 0
}

func (h *handle) Release(ctx context.Context) syscall.Errno {
	_ = h.Close()
	return 0
}

func main() {
	mount := flag.String("mount", "", "mount point")
	source := flag.String("source", "/data", "source directory")
	uid := flag.Uint("uid", 0, "file owner uid")
	gid := flag.Uint("gid", 0, "file owner gid")
	flag.Parse()

	if *mount == "" {
		log.Fatal("missing -mount")
	}
	if err := os.MkdirAll(*mount, 0700); err != nil {
		log.Fatal(err)
	}

	root := &node{src: *source, uid: uint32(*uid), gid: uint32(*gid)}

	opts := &fuse.MountOptions{
		AllowOther:        true,
		DirectMountStrict: true,
		DirectMountFlags:  syscall.MS_RDONLY | syscall.MS_NOSUID | syscall.MS_NODEV,
		MaxWrite:          1 << 20,
		MaxReadAhead:      1 << 17,
		FsName:            "vlcrootfs",
		Name:              "vlcrootfs",
		DisableXAttrs:     true,
		Options:           []string{"ro", "default_permissions", "context=u:object_r:app_data_file:s0"},
	}

	server, err := fs.Mount(*mount, root, opts)
	if err != nil {
		// 某些内核/FUSE 版本不接受 context=，退回不带 SELinux 上下文的挂载
		log.Printf("mount with context failed: %v; retry without context", err)
		opts.Options = []string{"ro", "default_permissions"}
		server, err = fs.Mount(*mount, root, opts)
	}
	if err != nil {
		log.Fatal(err)
	}
	server.Wait()
}
