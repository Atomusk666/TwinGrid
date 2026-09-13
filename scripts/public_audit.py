"""Read-only publication checks. Reports paths/rule IDs, never matched values.

With a populated Git index, audit all tracked files. Otherwise inspect the
source tree excluding only generated/local directories. Also checks dependency
hashes, SQLite decoded fields, ZIP/JAR contents, and local Markdown targets.
No scanner can prove the absence of every possible secret or license issue.
"""
import argparse, collections, gzip, hashlib, io, json, pathlib, re, sqlite3, subprocess, zipfile
P=pathlib.Path(__file__).resolve().parents[1]
SKIP={'.git','build','artifacts','logs','__pycache__','.venv'}
FORBIDDEN={'.apk','.keystore','.jks','.p12','.pfx','.pem','.key','.nds','.srl','.dsi','.sav','.dsv','.dss','.state','.img','.iso','.log','.zip','.7z','.tar'}
RULES={
 'private_key':rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
 'github_token':rb'(?:github_pat_[A-Za-z0-9_]{30,}|gh[pousr]_[A-Za-z0-9]{30,})',
 'llm_token':rb'sk-(?:proj-|ant-)?[A-Za-z0-9_-]{32,}',
 'google_key':rb'AIza[0-9A-Za-z_-]{35}',
 'aws_key':rb'AKIA[0-9A-Z]{16}',
 'private_host_path':rb'(?i)(?:[a-z]:[\\/](?:Users|Private)[\\/][a-z0-9_.-]+|/Users/[a-z0-9_.-]+)',
 'credential_assignment':rb'''(?i)(?:api[_-]?key|auth[_-]?token|storePassword|keyPassword|wifiPassword)\s*[=:]\s*["'][A-Za-z0-9_./+\-=]{12,}["']''',
}
def audit():
 findings=[];counts=collections.Counter()
 def problem(path,rule):findings.append({'path':path,'rule':rule})
 def scan(path,data):
  counts['payloads']+=1
  for label,pattern in RULES.items():
   if re.search(pattern,data):problem(path,label)
 def archive(path,data):
  with zipfile.ZipFile(io.BytesIO(data)) as z:
   for n in z.namelist():
    if n.endswith('/'):continue
    b=z.read(n);counts['archiveEntries']+=1;scan(path+'!'+n,b)
    if pathlib.PurePosixPath(n).suffix.lower() in FORBIDDEN:problem(path+'!'+n,'forbidden_archive_member')
    if n.endswith('.gz'):
     try:scan(path+'!'+n+'!decoded',gzip.decompress(b))
     except (OSError,EOFError):problem(path+'!'+n,'invalid_gzip')
 indexed={}
 try:
  r=subprocess.run(['git','ls-files','-s','-z'],cwd=P,capture_output=True,check=True)
  entries=[s.split(b'\t',1) for s in r.stdout.split(b'\0') if s]
  files=[P/name.decode('utf8') for _,name in entries]
  if entries:
   ids=[meta.split()[1] for meta,_ in entries]
   blobs=subprocess.run(['git','cat-file','--batch'],cwd=P,input=b'\n'.join(ids)+b'\n',capture_output=True,check=True).stdout
   stream=io.BytesIO(blobs)
   for (_,name),oid in zip(entries,ids):
    got,kind,size=stream.readline().split();assert got==oid and kind==b'blob'
    data=stream.read(int(size));assert stream.read(1)==b'\n'
    indexed[name.decode('utf8')]=data
 except (subprocess.CalledProcessError,FileNotFoundError):files=[]
 if not files:files=[f for f in P.rglob('*') if f.is_file() and not any(x in SKIP for x in f.relative_to(P).parts)]
 inventory=[]
 for f in sorted(files):
  n=f.relative_to(P).as_posix();disk=f.read_bytes();data=indexed.get(n,disk);counts['files']+=1;counts['bytes']+=len(data)
  if n in indexed and data!=disk:
   if n.startswith(('app/','catalog/')) or data!=disk.replace(b'\r\n',b'\n'):problem(n,'index_worktree_mismatch')
  inventory.append({'path':n,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
  if f.is_symlink():problem(n,'symlink')
  if f.suffix.lower() in FORBIDDEN or f.name.lower().startswith(('signing','.env')):problem(n,'forbidden_filename')
  if len(data)>25*1024*1024:problem(n,'unexpected_large_file')
  scan(n,data)
  if zipfile.is_zipfile(f):archive(n,data)
  if data.startswith(b'SQLite format 3\0'):
   with sqlite3.connect(f.resolve().as_uri()+'?mode=ro',uri=True) as db:
    assert db.execute('pragma integrity_check').fetchone()[0]=='ok'
    for (table,) in db.execute("select name from sqlite_master where type='table'").fetchall():
     assert re.fullmatch('[a-z_]+',table)
     for row in db.execute('select * from '+table):
      counts['databaseRows']+=1
      for v in row:
       if isinstance(v,bytes):problem(n+'::'+table,'database_blob')
       elif isinstance(v,str):
        scan(n+'::'+table,v.encode('utf8'))
        if re.search(r'/storage/emulated/|/data/(?:user|media)/|data:(?:image|audio|video)/',v):problem(n+'::'+table,'private_or_media_data')
  if f.suffix=='.md':
   for target in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)',data.decode('utf8')):
    target=target.split('#',1)[0]
    if target and not re.match(r'[a-z]+:',target) and not (f.parent/target).exists():problem(n,'broken_local_link:'+target)
 for d in json.loads((P/'docs/dependencies.json').read_text('utf8')):
  assert hashlib.sha256((P/'app/libs'/d['file']).read_bytes()).hexdigest()==d['sha256']
 from final_resource_gate import resources
 resources(P/'app/res')
 unique=list({(f['path'],f['rule']):f for f in findings}.values())
 return {'status':'PASS' if not unique else 'FAIL','scope':'Git index if populated; otherwise non-generated source tree','counts':dict(counts),'findings':unique,'inventory':inventory}
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--output',type=pathlib.Path);a.add_argument('--apk',type=pathlib.Path);x=a.parse_args();r=audit()
 if x.apk:
  from final_resource_gate import apk
  apk(x.apk)
  with zipfile.ZipFile(x.apk) as z:
   for n in z.namelist():
    for label,pattern in RULES.items():
     if re.search(pattern,z.read(n)):r['findings'].append({'path':'APK!'+n,'rule':label})
  r['apkSHA256']=hashlib.sha256(x.apk.read_bytes()).hexdigest()
  r['status']='PASS' if not r['findings'] else 'FAIL'
 if x.output:x.output.parent.mkdir(parents=True,exist_ok=True);x.output.write_text(json.dumps(r,ensure_ascii=False,indent=2),'utf8')
 print(json.dumps({k:v for k,v in r.items() if k!='inventory'},ensure_ascii=False,indent=2))
 raise SystemExit(0 if r['status']=='PASS' else 1)
