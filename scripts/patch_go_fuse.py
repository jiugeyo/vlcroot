#!/usr/bin/env python3
"""Patch vendored go-fuse for Android 16.

go-fuse's parseRequest() returns ENOSYS for unknown opcodes **without writing
any reply**, so the calling process blocks forever. Android 16 / Xiaomi
kernels send newer opcodes (observed: 2016) which triggers exactly that.

We install a fallback operationHandler that simply sets ENOSYS; the normal
handleRequest path then serializes a proper error reply.
"""

from pathlib import Path

root = Path(__file__).resolve().parent.parent
request_go = root / "rootfsd" / "vendor" / "github.com" / "hanwen" / "go-fuse" / "v2" / "fuse" / "request.go"

src = request_go.read_text()

old = """\th = getHandler(hdr.Opcode)
\tif h == nil {
\t\tlog.Printf("Unknown opcode %d", hdr.Opcode)
\t\terrno = ENOSYS
\t\treturn
\t}"""

new = """\th = getHandler(hdr.Opcode)
\tif h == nil {
\t\t// Android 16 may send opcodes unknown to this go-fuse version
\t\t// (observed 2016 / statx+bpf extensions). Reply ENOSYS instead of
\t\t// returning without a reply, otherwise the caller hangs forever.
\t\tlog.Printf("Unknown opcode %d; replying ENOSYS", hdr.Opcode)
\t\th = &unknownOpcodeHandler
\t}"""

if "unknownOpcodeHandler" in src:
    print("already patched")
    raise SystemExit(0)

if old not in src:
    raise SystemExit("parseRequest pattern not found; go-fuse version changed")

src = src.replace(old, new, 1)
marker = "func parseRequest(in []byte, kernelSettings *InitIn)"
handler = (
    "var unknownOpcodeHandler = operationHandler{\n"
    "\tName: \"UNKNOWN\",\n"
    "\tFunc: func(_ *protocolServer, req *request) { req.status = ENOSYS },\n"
    "}\n\n"
)
if marker not in src:
    raise SystemExit("parseRequest function not found")
src = src.replace(marker, handler + marker, 1)
request_go.write_text(src)
print("patched", request_go)
