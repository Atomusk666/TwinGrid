"""Resource whitelist plus decoded SQLite/media checks, before building and at delivery."""
import gzip,json,pathlib,re,sqlite3,sys,zipfile,hashlib
P=pathlib.Path(__file__).resolve().parent.parent
RES={'drawable/ic_launcher.xml','raw/nds_catalog.sqlite','raw/nds_catalog_manifest.json','raw/nds_pinyin.txt','raw/runtime_licenses.txt','raw/fusion_pixel_license.txt','font/fusion_pixel_10.ttf','values/colors.xml','values/strings.xml','values/styles.xml','values-zh/strings.xml','xml/locales_config.xml'}
HOST_ONLY={'raw/bangumi_nds_seed.json'}
RES.add('raw/overview_copy.json')  # Reviewed text-only drafts, separate from immutable catalog prose.
RES.update({'raw/cover_index.json','raw/cover_index_manifest.json'})  # Public path/digest index, no image payload.
RES.update({'values/code112_catalog_strings.xml','values-en/code112_catalog_strings.xml'})
RES.update({'values/journey115.xml','values-zh/journey115.xml','values-en/journey115.xml'})
RES.update({'values/battery_strings.xml','values-zh/battery_strings.xml'})
MEDIA_SUFFIX={'.png','.jpg','.jpeg','.gif','.webp','.bmp','.ico','.avif','.mp4','.webm','.ogg','.mp3','.wav','.img','.dds','.ktx'}
def media(name,b):
 assert pathlib.PurePosixPath(name).suffix.lower() not in MEDIA_SUFFIX,('MEDIA_NAME',name)
 assert not (b.startswith((b'\x89PNG',b'\xff\xd8\xff',b'GIF87a',b'GIF89a',b'BM',b'OggS',b'ID3')) or b[:4]==b'RIFF' and b[8:12] in [b'WEBP',b'WAVE'] or b[4:8]==b'ftyp'),('MEDIA_MAGIC',name)
def database(path):
 db=sqlite3.connect(path.resolve().as_uri()+'?mode=ro',uri=True);assert db.execute('pragma integrity_check').fetchone()[0]=='ok'
 counts={};forbidden=[]
 for (table,) in db.execute("select name from sqlite_master where type='table'").fetchall():
  assert re.fullmatch('[a-z_]+',table);counts[table]=db.execute('select count(*) from '+table).fetchone()[0]
  for row in db.execute('select * from '+table):
   for value in row:
    assert not isinstance(value,bytes),('BLOB',table)
    if isinstance(value,str):
     assert not re.search(r'(?i)data:(?:image|audio|video)/|/data/(?:user|media)/|/storage/emulated/|RGDSV08_TEST_|"(?:imageBase64|coverData|screenshots)"\s*:',value),('RUNTIME_OR_MEDIA_PAYLOAD',table)
 db.close();return counts
def resources(root):
 from production_catalog_gate import verify
 verify(P,root/'raw')
 actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()};assert actual<=RES|HOST_ONLY,('UNAPPROVED_RESOURCE',sorted(actual-RES-HOST_ONLY));assert RES<=actual,('MISSING_RESOURCE',RES-actual)
 for name in RES:
  data=(root/name).read_bytes();media(name,data)
  if name.endswith(('.xml','.json','.txt')):data.decode('utf8')
  if name=='font/fusion_pixel_10.ttf':assert hashlib.sha256(data).hexdigest()=='04de2c9adf78db676bd910fd229e16c1d70230b776579049ce82f7c6a06703f2','UNAPPROVED_FONT'
 manifest=json.loads((root/'raw/nds_catalog_manifest.json').read_text('utf8'));raw=(root/'raw/nds_catalog.sqlite').read_bytes();assert hashlib.sha256(raw).hexdigest()==manifest['sha256'] and len(raw)==manifest['bytes']
 cover_manifest=json.loads((root/'raw/cover_index_manifest.json').read_text('utf8'));cover_bytes=(root/'raw/cover_index.json').read_bytes();assert len(cover_bytes)==cover_manifest['bytes'] and hashlib.sha256(cover_bytes).hexdigest()==cover_manifest['sha256']
 cover=json.loads(cover_bytes);assert len(cover['tree'])==cover_manifest['resources'] and re.fullmatch('[a-f0-9]{40}',cover['revision'])
 assert len({r['path'] for r in cover['tree']})==len(cover['tree']) and all(set(r)=={'path','sha','bytes'} and r['path'].startswith('Named_Boxarts/') and r['path'].endswith('.png') and re.fullmatch('[a-f0-9]{40}',r['sha']) and isinstance(r['bytes'],int) for r in cover['tree'])
 drafts=json.loads((root/'raw/overview_copy.json').read_text('utf8'))['drafts']
 assert len({(d['workId'],d['locale']) for d in drafts})==len(drafts)
 db=sqlite3.connect((root/'raw/nds_catalog.sqlite').resolve().as_uri()+'?mode=ro',uri=True)
 # Capacity follows the real catalog, not an arbitrary small editorial batch.
 # Each draft still requires a unique work/locale and exact text/evidence digests.
 assert len(drafts)<=2*db.execute('select count(*) from works').fetchone()[0]
 assert (root/'raw/overview_copy.json').stat().st_size<=max(262144,len(drafts)*2048)
 for draft in drafts:
  locale=draft['locale'];assert locale in ['zh','en'] and 1<=len(draft['text'])<=320
  source=db.execute('SELECT summary,evidence FROM localized_content WHERE work_id=? AND locale=?',(draft['workId'],locale)).fetchone() if locale=='en' else db.execute('SELECT summary_zh,evidence FROM content WHERE work_id=?',(draft['workId'],)).fetchone()
  assert source and hashlib.sha256(source[0].encode()).hexdigest()==draft['sourceSHA256']
  assert hashlib.sha256(source[1].encode()).hexdigest()==draft['evidenceSHA256']
 db.close()
 return {'status':'PASS','resourceWhitelist':sorted(RES),'explicitHostOnlyExcluded':sorted(HOST_ONLY),'sqliteRows':database(root/'raw/nds_catalog.sqlite'),'catalogSHA256':manifest['sha256']}
def apk(path):
 with zipfile.ZipFile(path) as z:
  allowed={'AndroidManifest.xml','resources.arsc','classes.dex','res/drawable/ic_launcher.xml','res/xml/locales_config.xml',*[ 'res/'+x for x in RES if x.startswith(('raw/','font/'))],'okhttp3/internal/publicsuffix/publicsuffixes.gz','okhttp3/internal/publicsuffix/NOTICE'}
  names=set(z.namelist());assert all(n in allowed or re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[A-Z0-9_]+\.(?:SF|RSA))',n) for n in names),('UNEXPECTED_APK_ENTRY',names-allowed)
  for info in z.infolist():media(info.filename,z.read(info))
  suffix=gzip.decompress(z.read('okhttp3/internal/publicsuffix/publicsuffixes.gz'));assert suffix and not suffix.startswith(b'\x89PNG')
  manifest=json.loads(z.read('res/raw/nds_catalog_manifest.json'));raw=z.read('res/raw/nds_catalog.sqlite');assert hashlib.sha256(raw).hexdigest()==manifest['sha256']
  lock=json.loads((P/'catalog/production_catalog.lock.json').read_text('utf8'))
  for name,digest in lock['files'].items():assert hashlib.sha256(z.read('res/raw/'+name)).hexdigest()==digest,('APK_CATALOG_NOT_REGISTERED',name)
  cm=json.loads(z.read('res/raw/cover_index_manifest.json'));ci=z.read('res/raw/cover_index.json');assert len(ci)==cm['bytes'] and hashlib.sha256(ci).hexdigest()==cm['sha256']
 return {'status':'PASS','apk':str(path),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'entries':sorted(names),'gameMedia':0,'runtimeUserCache':0,'catalogSHA256':manifest['sha256']}
if __name__=='__main__':print(json.dumps(apk(pathlib.Path(sys.argv[1])) if len(sys.argv)>1 else resources(P/'app/res'),ensure_ascii=False,indent=2))
