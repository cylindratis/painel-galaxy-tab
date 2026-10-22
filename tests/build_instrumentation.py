"""Build the separate API-8 QA APK; use the app's signing key."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--java', default=os.environ.get('JAVA8') or shutil.which('java') or 'java')
parser.add_argument('--tools', type=Path, default=Path(os.environ.get('PAINEL_TOOLS', str(root / 'android/tools'))))
parser.add_argument('--keystore', type=Path, default=root / 'android/build/signing/casa-sala.jks')
args = parser.parse_args()
tools = args.tools.resolve()
build = root / 'android/build/instrumentation'
classes = build / 'classes'
classes.mkdir(parents=True, exist_ok=True)
android = tools / 'android-2.2_r03-linux/android.jar'
sdk = tools / 'android-9'
def run(*items):
    subprocess.run([str(x) for x in items], check=True)
run(args.java, '-jar', tools / 'ecj.jar', '-1.6', '-encoding', 'UTF-8', '-nowarn',
    '-bootclasspath', android, '-classpath', root / 'android/build/classes', '-d', classes,
    root / 'tests/V8SmokeTest.java', root / 'tests/V8ReleaseCheck.java')
run(args.java, '-jar', sdk / 'lib/dx.jar', '--dex', '--min-sdk-version=8',
    '--output=' + str(build / 'classes.dex'), classes)
run(sdk / ('aapt.exe' if os.name == 'nt' else 'aapt'), 'package', '-f',
    '-M', root / 'tests/AndroidManifest.xml', '-I', android, '-F', build / 'unsigned.apk')
with zipfile.ZipFile(build / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    apk.write(build / 'classes.dex', 'classes.dex')
password = os.environ.get('PAINEL_KS_PASS', 'painel-development')
run(args.java, '-jar', sdk / 'lib/apksigner.jar', 'sign', '--ks', args.keystore.resolve(),
    '--ks-key-alias', 'casa-sala', '--ks-pass', 'pass:' + password, '--key-pass', 'pass:' + password,
    '--min-sdk-version', '8', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'false',
    '--out', build / 'test.apk', build / 'unsigned.apk')
print('QA APK:', build / 'test.apk')
