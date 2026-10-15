#!/usr/bin/env python3
"""Guard the three Android configuration entry points against missing settings.

This does NOT certify the PHP portal, whose current source is in another repo.
It checks the shared Android store / TV form / LAN form and remote validator.
"""
import pathlib
import re
import sys

ROOT=pathlib.Path(__file__).resolve().parents[1] / "app/src/main/java/com/digitalsignage/tv"
store=(ROOT/"SettingsStore.kt").read_text()
tv=(ROOT/"SettingsActivity.kt").read_text()
lan=(ROOT/"LocalAdminServer.kt").read_text()
remote=(ROOT/"DeviceConfigSync.kt").read_text()
expected={
    "auto_start":"autoStart", "splash":"splash", "video_audio":"videoAudio",
    "offline_playback":"offlinePlayback", "enabled_modes":"enabledModes",
    "primary_mode":"primaryMode", "sync_minutes":"syncMinutes", "cache_mb":"cacheMb",
    "external_cache":"externalCache", "update_policy":"updatePolicy",
    "update_channel":"updateChannel", "overlay_interval_sec":"overlayIntervalSec",
    "overlay_duration_sec":"overlayDurationSec", "signage_show_hud":"signageShowHud",
    "signage_hud_seconds":"signageHudSeconds", "overlay_layout":"overlayLayout",
    "overlay_width_pct":"overlayWidthPct","overlay_height_pct":"overlayHeightPct",
    "overlay_opacity":"overlayOpacity","smart_app":"smartApp",
    "smart_header_pct":"smartHeaderPct","smart_poll_sec":"smartPollSec",
    "smart_sound":"smartSound",
}
def fail(msg):
    print("CONFIG PARITY FAILED:",msg,file=sys.stderr)
    sys.exit(1)

if "fun publicSnapshot()" not in store or "fun validateSettings(" not in store or "fun applySnapshot(" not in store:
    fail("shared SettingsStore contract missing")
start=store.index("fun publicSnapshot()")
end=store.index("var overlayIntervalSec",start)
snapshot=set(re.findall(r'put\("([a-z_]+)"',store[start:end]))
if snapshot!=set(expected):
    fail("shared snapshot mismatch: missing %s / unexpected %s"%(sorted(set(expected)-snapshot),sorted(snapshot-set(expected))))

v_start=store.index("val allowed=setOf(",store.index("fun validateSettings("))
v_end=store.index(")",v_start)
allowlist=set(re.findall(r'"([a-z_]+)"',store[v_start:v_end]))
if allowlist!=set(expected):
    fail("validation allow-list mismatch: %s"%(sorted(allowlist^set(expected))))

apply=store[store.index("fun applySnapshot("):store.index("fun applySnapshot(")+2100]
for key,prop in expected.items():
    if not re.search(r'config\.'+re.escape(prop)+r'\b',tv):
        fail("TV form omits "+key+" / "+prop)
    if '"'+key+'"' not in lan:
        fail("LAN form/save omits "+key)
    if '"'+key+'"' not in apply:
        fail("atomic persistence omits "+key)
if "for((bit,title) in listOf(1 to" not in lan or "mode_$bit" not in lan:
    fail("LAN mode mask isn't made from visible checkboxes")
if "settings.validateSettings(" not in remote or "settings.applySnapshot(" not in remote:
    fail("remote config bypasses canonical validation/persistence")
if 'debug_hud' not in lan or 'debugHudEnabled' not in tv:
    fail("support HUD must be available on TV and LAN")
print("CONFIG PARITY OK:",len(expected),"shared settings in Android TV, LAN, store, validator and remote sync.")
print("WEB PORTAL CHECK: NOT RUN (current PHP source not present in this repository).")
