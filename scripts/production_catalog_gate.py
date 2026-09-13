"""Fail closed if a legacy compiler replaced the registered production pair."""
from pathlib import Path
import hashlib,json,sqlite3
def verify(project, raw=None):
    project=Path(project);raw=Path(raw) if raw else project/'app/res/raw'
    lock=json.loads((project/'catalog/production_catalog.lock.json').read_text('utf8'))
    for name,digest in lock['files'].items():
        for root in [raw,project/lock['source']]:
            assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest, ('Production catalog differs from registered source; review/merge and register a new pair, do not run a legacy compiler',str(root/name))
    m=json.loads((raw/'nds_catalog_manifest.json').read_text('utf8'))
    with sqlite3.connect((raw/'nds_catalog.sqlite').resolve().as_uri()+'?mode=ro',uri=True) as db:
        info=dict(db.execute('select * from info'))
    assert info['version']==m['version']==lock['version']
    assert info['content_sha256']==m['contentSHA256'] and info['schema']==str(m['schema'])
    assert (raw/'nds_catalog.sqlite').stat().st_size==m['bytes']
    return lock
if __name__=='__main__': print(json.dumps(verify(Path(__file__).resolve().parents[1]),indent=2))
