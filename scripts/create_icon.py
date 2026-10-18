#!/usr/bin/env python3
"""Verify and materialize the approved 2Cam brand; no generated replacement logo."""
from pathlib import Path
import base64, hashlib
root=Path(__file__).resolve().parents[1]
for name in ('brand_logo','ic_2cam_signage'):
    data=base64.b64decode((root/'branding'/f'{name}.png.b64').read_text(),validate=False)
    expected=(root/'branding'/f'{name}.sha256').read_text().strip()
    if hashlib.sha256(data).hexdigest()!=expected:raise SystemExit('Brand checksum mismatch')
    dest=root/'app/src/main/res/drawable-nodpi'/f'{name}.png'
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    print('Approved brand resource:',name,len(data))
