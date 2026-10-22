"""Build an API-8 APK without Gradle. Requires Java 8; downloads official SDK files."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def download(url, dest):
    if dest.exists():
        return
    print('Baixando', dest.name, flush=True)
    partial = dest.with_suffix(dest.suffix + '.part')
    with urllib.request.urlopen(url, timeout=120) as source, partial.open('wb') as output:
        shutil.copyfileobj(source, output)
    partial.replace(dest)


def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tools', type=Path, default=ROOT / 'tools')
    parser.add_argument('--keystore', type=Path, help='Existing private signing key (outside Git)')
    parser.add_argument('--ks-pass', default=os.environ.get('PAINEL_KS_PASS', 'painel-development'))
    parser.add_argument('--java', default=shutil.which('java') or 'java')
    parser.add_argument('--output', type=Path, default=ROOT / 'build' / 'CasaSala.apk')
    args = parser.parse_args()
    tools = args.tools.resolve()
    tools.mkdir(parents=True, exist_ok=True)
    windows = os.name == 'nt'
    platform = 'windows' if windows else 'linux'
    download('https://dl.google.com/android/repository/android-2.2_r03-linux.zip', tools / 'platform-8.zip')
    download('https://dl.google.com/android/repository/build-tools_r28.0.3-%s.zip' % platform, tools / 'build-tools.zip')
    download('https://repo.maven.apache.org/maven2/org/eclipse/jdt/core/compiler/ecj/4.6.1/ecj-4.6.1.jar', tools / 'ecj.jar')
    if not (tools / 'android-2.2_r03-linux' / 'android.jar').exists():
        with zipfile.ZipFile(tools / 'platform-8.zip') as archive:
            archive.extractall(tools)
    if not (tools / 'android-9').exists():
        with zipfile.ZipFile(tools / 'build-tools.zip') as archive:
            archive.extractall(tools)
    sdk = tools / 'android-9'
    ext = '.exe' if windows else ''
    aapt, align = sdk / ('aapt' + ext), sdk / ('zipalign' + ext)
    if not windows:
        aapt.chmod(0o755)
        align.chmod(0o755)
    build = ROOT / 'build'
    classes = build / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    android = tools / 'android-2.2_r03-linux' / 'android.jar'
    sources = sorted((ROOT / 'src').rglob('*.java'))
    run(args.java, '-jar', tools / 'ecj.jar', '-1.6', '-encoding', 'UTF-8', '-nowarn',
        '-bootclasspath', android, '-d', classes, *sources)
    run(args.java, '-Xmx512m', '-jar', sdk / 'lib' / 'dx.jar', '--dex', '--min-sdk-version=8',
        '--output=' + str(build / 'classes.dex'), classes)
    unsigned = build / 'unsigned.apk'
    run(aapt, 'package', '-f', '-M', ROOT / 'AndroidManifest.xml', '-S', ROOT / 'res', '-A', ROOT / 'assets', '-I', android, '-F', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(build / 'classes.dex', 'classes.dex')
    aligned = build / 'aligned.apk'
    run(align, '-f', '4', unsigned, aligned)
    signing = ROOT / 'build' / 'signing'
    signing.mkdir(exist_ok=True)
    key = args.keystore.resolve() if args.keystore else signing / 'casa-sala.jks'
    if not key.exists():
        java_bin = Path(args.java).resolve().parent if Path(args.java).is_file() else Path(shutil.which(args.java)).parent
        keytool = java_bin / ('keytool' + ext)
        run(keytool, '-genkeypair', '-keystore', key, '-storepass', args.ks_pass, '-keypass', args.ks_pass,
            '-alias', 'casa-sala', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Painel Development, O=Painel, C=BR', '-storetype', 'JKS')
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    run(args.java, '-jar', sdk / 'lib' / 'apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', 'casa-sala',
        '--ks-pass', 'pass:' + args.ks_pass, '--key-pass', 'pass:' + args.ks_pass, '--min-sdk-version', '8',
        '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'false',
        '--out', output, aligned)
    run(args.java, '-jar', sdk / 'lib' / 'apksigner.jar', 'verify', '--min-sdk-version', '8', '--verbose', output)
    run(aapt, 'dump', 'badging', output)
    print('APK pronto:', output)


if __name__ == '__main__':
    main()
