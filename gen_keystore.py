import subprocess
import os

cmd = [
    "openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", "key.pem", "-out", "cert.pem",
    "-days", "10000", "-nodes", "-subj", "/CN=AutoBot/O=Personal/C=IN"
]
subprocess.run(cmd, check=True)
subprocess.run(["openssl", "pkcs12", "-export", "-in", "cert.pem", "-inkey", "key.pem", "-out", "keystore/release.jks", "-name", "autobot_key", "-password", "pass:autobot_secret_key_pass"], check=True)
os.remove("key.pem")
os.remove("cert.pem")
print("PKCS12 Keystore generated successfully!")
