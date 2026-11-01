#!/usr/bin/env python3
"""Convert the user's JGjiuge.key container into a PKCS#12 keystore.

The supplied file is a simple container:

    4 bytes   magic/checksum
    4 bytes   big-endian length of the PEM certificate
    ...       PEM certificate ("-----BEGIN CERTIFICATE-----")
    4 bytes   big-endian length of the DER private key
    ...       PKCS#8 DER RSA private key

No private key material is printed. The resulting PKCS#12 is written to the
path given as argv[2] and encrypted with the password in argv[3].
"""

import os
import struct
import subprocess
import sys
from pathlib import Path

BEGIN = b"-----BEGIN CERTIFICATE-----"
END = b"-----END CERTIFICATE-----"


def fail(msg: str) -> None:
    print("extract_signing_key: " + msg, file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if len(sys.argv) != 5:
        fail("usage: extract_signing_key.py <input.key> <out.p12> <password> <alias>")

    source = Path(sys.argv[1])
    out = Path(sys.argv[2])
    password = sys.argv[3]
    alias = sys.argv[4]

    data = source.read_bytes()
    start = data.find(BEGIN)
    if start < 0:
        fail("certificate PEM not found in input")
    end = data.find(END, start)
    if end < 0:
        fail("certificate PEM end not found")
    end += len(END)
    cert = data[start:end]

    if end + 4 > len(data):
        fail("truncated input after certificate")
    key_len = struct.unpack(">I", data[end:end + 4])[0]
    if key_len <= 0 or end + 4 + key_len > len(data):
        fail("invalid private key length in input")
    key = data[end + 4:end + 4 + key_len]
    if not key.startswith(b"\x30"):
        fail("private key is not PKCS#8 DER")

    cert_path = out.with_suffix(".pem")
    key_path = out.with_suffix(".der")
    key_pem_path = out.with_suffix(".key.pem")
    cert_path.write_bytes(cert)
    key_path.write_bytes(key)

    env = os.environ.copy()
    env["VLCROOT_SIGNING_PASSWORD"] = password
    try:
        # OpenSSL's pkcs12 -inkey expects PEM; convert the PKCS#8 DER first.
        subprocess.run(
            [
                "openssl", "pkey", "-inform", "DER",
                "-in", str(key_path), "-out", str(key_pem_path),
            ],
            check=True,
            env=env,
        )
        key_pub = subprocess.check_output(
            ["openssl", "pkey", "-in", str(key_pem_path), "-pubout"],
            env=env,
        )
        cert_pub = subprocess.check_output(
            ["openssl", "x509", "-in", str(cert_path), "-pubkey", "-noout"],
            env=env,
        )
        if key_pub.split() != cert_pub.split():
            fail("certificate and private key do not match")
        subprocess.run(
            ["openssl", "x509", "-in", str(cert_path), "-noout", "-subject", "-dates"],
            check=True,
            env=env,
        )
        subprocess.run(
            [
                "openssl", "pkcs12", "-export",
                "-inkey", str(key_pem_path),
                "-in", str(cert_path),
                "-name", alias,
                "-passout", "env:VLCROOT_SIGNING_PASSWORD",
                "-out", str(out),
            ],
            check=True,
            env=env,
        )
    finally:
        # The DER/private key is plaintext; don't leave it around.
        key_path.unlink(missing_ok=True)
        key_pem_path.unlink(missing_ok=True)

    if not out.exists() or out.stat().st_size == 0:
        fail("openssl did not produce a keystore")

    print(f"created {out} alias={alias} bytes={out.stat().st_size}")


if __name__ == "__main__":
    main()
