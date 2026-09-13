"""Read-only guard for the existing deferred UiStrings resolver contract."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
P=Path(__file__).resolve().parents[1]
keys={e.attrib['name'] for f in (P/'app/res/values').glob('*.xml') for e in ET.fromstring(f.read_text('utf8')).findall('string')}
count=0
for f in (P/'app/src').rglob('*.java'):
    for key in re.findall(r'UiStrings.msg\("([^"]+)"\)',f.read_text('utf8')):
        if not re.fullmatch('ui_[a-f0-9]+',key) or key not in keys:
            raise SystemExit('Unresolvable UI resource: '+f.relative_to(P).as_posix()+' '+key)
        count+=1
print('UI_RESOURCE_KEYS_PASS',count)
