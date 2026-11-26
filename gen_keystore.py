import subprocess
import os

os.makedirs("keystore", exist_ok=True)
cmd = [
    "openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", "key.pem", "-out", "cert.pem",
    "-days", "20000", "-nodes", "-subj", "/CN=AutoBot/OU=Personal/O=Personal/L=India/ST=India/C=IN"
]
subprocess.run(cmd, check=True)
subprocess.run([
    "openssl", "pkcs12", "-export", "-in", "cert.pem", "-inkey", "key.pem",
    "-out", "keystore/release.jks", "-name", "autobot_key",
    "-password", "pass:autobot_secret_key_pass"
], check=True)
os.remove("key.pem")
os.remove("cert.pem")
print("Permanent release.jks generated!")
