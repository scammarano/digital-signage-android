#!/usr/bin/env python3
"""Reject accidental signer rotation. Private key stays in CI secret/cache."""
import base64,hashlib,os,pathlib,subprocess
expected='f71cd12b1f376b39afd3ecb31b13237d7c13589ab07dacbb680f8fd7e3841f21'
path=pathlib.Path.home()/'.android/debug.keystore'
encoded=os.environ.get('SIGNAGE_DEBUG_KEYSTORE_B64','')
if encoded:
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(base64.b64decode(encoded,validate=True));path.chmod(0o600)
if not path.is_file():
 raise SystemExit('Pinned signing key missing. Restore original keystore via SIGNAGE_DEBUG_KEYSTORE_B64. Refusing to create a different signer.')
cert=subprocess.check_output(['keytool','-exportcert','-alias','androiddebugkey','-keystore',str(path),'-storepass','android'],stderr=subprocess.PIPE)
actual=hashlib.sha256(cert).hexdigest()
if actual!=expected:raise SystemExit('Signing certificate differs from v0.4.13. Build stopped to preserve in-place updates.')
print('SIGNER_PIN_OK sha256:'+actual)
