"""Assemble unsigned APK, including OkHttp's required text runtime resource.
Optimization changes DEFLATE level only here. Signing always follows zipalign.
"""
import argparse,pathlib,zipfile,copy
P=pathlib.Path(__file__).resolve().parent.parent
def assemble(apk,dex,opt):
 level=9 if opt else 6;temp=apk.with_suffix('.assembling.apk')
 with zipfile.ZipFile(apk) as src,zipfile.ZipFile(temp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=level) as dst:
  for info in src.infolist():
   assert not info.filename.startswith('META-INF/') and info.filename!='classes.dex'
   dst.writestr(copy.copy(info),src.read(info),compress_type=info.compress_type,compresslevel=level)
  dst.writestr('classes.dex',dex.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=level)
  with zipfile.ZipFile(P/'app/libs/okhttp-4.12.0.jar') as lib:
   for name in ['okhttp3/internal/publicsuffix/publicsuffixes.gz','okhttp3/internal/publicsuffix/NOTICE']:
    dst.writestr(name,lib.read(name),compress_type=zipfile.ZIP_DEFLATED,compresslevel=level)
 temp.replace(apk)
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('apk',type=pathlib.Path);a.add_argument('dex',type=pathlib.Path);a.add_argument('--optimized',action='store_true');x=a.parse_args();assemble(x.apk,x.dex,x.optimized)
