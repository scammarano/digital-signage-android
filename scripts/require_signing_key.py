#!/usr/bin/env python3
"""Reject accidental signer rotation. Private key stays in CI secret/cache."""
import base64,hashlib,os,pathlib,subprocess
expected='52468eda51fbe7de6886fd800874f73566bdaa7165732e96f03764483840ec2f'
path=pathlib.Path.home()/'.android/debug.keystore'
encoded=os.environ.get('SIGNAGE_DEBUG_KEYSTORE_B64','')
if encoded:
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(base64.b64decode(encoded,validate=True));path.chmod(0o600)
if not path.is_file():
 raise SystemExit('Pinned signing key missing. Restore the 2026 permanent signing keystore via SIGNAGE_DEBUG_KEYSTORE_B64. Refusing to create a different signer.')
cert=subprocess.check_output(['keytool','-exportcert','-alias',os.environ.get('SIGNAGE_KEY_ALIAS','2cam-signage'),'-keystore',str(path),'-storepass:env','SIGNAGE_STORE_PASSWORD'],stderr=subprocess.PIPE)
actual=hashlib.sha256(cert).hexdigest()
if actual!=expected:raise SystemExit('Signing certificate differs from the permanent 2Cam signer. Build stopped to preserve in-place updates.')
print('SIGNER_PIN_OK sha256:'+actual)
