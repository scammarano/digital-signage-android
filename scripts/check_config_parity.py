#!/usr/bin/env python3
"""The three forms render one shared versioned schema."""
import json,pathlib
root=pathlib.Path(__file__).resolve().parents[1]
schema=json.loads((root/'app/src/main/assets/config-schema.json').read_text())
fields=schema['fields'];keys=[f['key'] for f in fields]
assert len(keys)==len(set(keys))
assert list(schema['modes'].values())==['Cartelera','Overlay','Turnos','Marcador deportivo']
assert schema['schema']==2
assert len(fields)==43
assert next(f for f in fields if f['key']=='update_channel')['options']=={'stable':'Solo estables','debug':'Estables y pruebas'}
assert not any(f['key'].endswith(('_width_pct','_height_pct')) for f in fields)
assert {'debug_hud_enabled','enabled_modes','primary_mode','queue_layout','scoreboard_layout'}<=set(keys)
for mode in ['overlay','queue','scoreboard']:
 for layout in schema['layouts']:assert mode+'_playlist_'+layout in keys
base=root/'app/src/main/java/com/digitalsignage/tv'
for file in ['SettingsActivity.kt','LocalAdminServer.kt']:
 assert 'schemaFields()' in (base/file).read_text()
store=(base/'SettingsStore.kt').read_text()
assert 'for(f in schemaFields())' in store and 'fun validateSettings(' in store
remote=(base/'DeviceConfigSync.kt').read_text()
assert 'settings.validateSettings(' in remote and 'settings.applySnapshot(' in remote
web=root.parent/'web-source'
if web.is_dir():assert (root/'app/src/main/assets/config-schema.json').read_bytes()==(web/'app/config-schema.json').read_bytes()
print('CONFIG PARITY OK:',len(fields),'fields; four modes and independent playlist assignments.')
print('Physical Fire TV and deployed PHP parity: require installed matching versions.')
