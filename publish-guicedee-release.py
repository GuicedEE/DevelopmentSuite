"""Publish the verified GuicedEE 2.3.0 artifacts in four resumable Central bundles.

Uses existing Maven install outputs; never builds, cleans, commits or tags.
Credentials stay in memory. GPG receives its passphrase through stdin.
Commands: prepare, run, upload <batch>, status <batch>, verify <batch>.
"""
from pathlib import Path
import argparse
import base64
import concurrent.futures
import hashlib
import http.client
import json
import os
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = '2.3.0'
OUT = ROOT / ('target/release-' + VERSION) / 'production'
BATCHES = ('versioner', 'boms', 'services', 'modules')
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
API = 'https://central.sonatype.com/api/v1/publisher/'
CENTRAL = 'https://repo.maven.apache.org/maven2/'


def digest(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def settings():
    root = ET.parse(Path.home() / '.m2/settings.xml').getroot()
    for node in root.iter():
        node.tag = node.tag.split('}')[-1]
    return root


def authorization():
    server = next(s for s in settings().findall('servers/server') if s.findtext('id') == 'central')
    values = []
    for key in ('username', 'password'):
        value = server.findtext(key) or ''
        if value.startswith('${env.') and value.endswith('}'):
            value = os.environ.get(value[6:-1], '')
        if not value or value.startswith('{'):
            raise RuntimeError('Central credentials are missing or encrypted; use the Maven credential provider')
        values.append(value)
    return 'Bearer ' + base64.b64encode(':'.join(values).encode()).decode()


def save(path, value):
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    os.replace(temporary, path)


def release_inventory():
    """Read the same GuicedEE profiles as build.ps1 and audit their installed outputs."""
    root_pom = ET.parse(ROOT / 'pom.xml').getroot()
    profiles = {
        profile.findtext('m:id', namespaces=NS): profile
        for profile in root_pom.findall('m:profiles/m:profile', NS)
    }
    modules = ['GuicedEE/bom/Versioner']
    for profile_name in ('guicedee-boms', 'services', 'guicedee'):
        modules.extend(node.text for node in profiles[profile_name].findall('m:modules/m:module', NS))
    assert len(modules) == len(set(modules)) == 116

    rows, audit = [], []
    for module_name in modules:
        module = ROOT / module_name
        pom = ET.parse(module / 'pom.xml').getroot()
        parent = pom.find('m:parent', NS)
        group_id = pom.findtext('m:groupId', namespaces=NS) or parent.findtext('m:groupId', namespaces=NS)
        version = pom.findtext('m:version', namespaces=NS) or parent.findtext('m:version', namespaces=NS)
        artifact_id = pom.findtext('m:artifactId', namespaces=NS)
        packaging = pom.findtext('m:packaging', default='jar', namespaces=NS)
        assert version == VERSION, (module_name, version)
        row = {'module': module_name, 'groupId': group_id, 'artifactId': artifact_id,
               'version': version, 'packaging': packaging}
        rows.append(row)
        if packaging == 'pom':
            continue
        stem = artifact_id + '-' + version
        jar = module / 'target' / (stem + '.jar')
        sources = module / 'target' / (stem + '-sources.jar')
        javadoc = module / 'target' / (stem + '-javadoc.jar')
        described = subprocess.run(['jar', '--describe-module', '--file', str(jar)],
                                   capture_output=True, text=True)
        module_version_matches = described.returncode == 0 and ('@' + version + ' ') in described.stdout
        audit.append({'module': module_name, 'artifactId': artifact_id, 'version': version,
                      'exists': jar.is_file(), 'sources': sources.is_file(),
                      'javadoc': javadoc.is_file(), 'moduleVersionMatches': module_version_matches})
    assert len(rows) == 116 and len(audit) == 101
    return rows, audit


def prepare():
    OUT.mkdir(parents=True, exist_ok=True)
    manifest_path = OUT / 'manifest.json'
    if manifest_path.exists():
        raise RuntimeError('A prepared release exists; use its immutable bundles and resume by batch')
    rows, audit = release_inventory()
    release_output = OUT.parent
    save(release_output / 'release-train.json', rows)
    save(release_output / 'release-artifacts.json', audit)
    assert len(rows) == 116 and len(audit) == 101
    assert all(all(r[k] for k in ('exists', 'sources', 'javadoc', 'moduleVersionMatches')) for r in audit)
    local = Path(settings().findtext('localRepository') or str(Path.home() / '.m2/repository'))
    manifest = {batch: {'components': [], 'files': []} for batch in BATCHES}
    passphrase = os.environ.get('MAVEN_GPG_PASSPHRASE')
    if not passphrase:
        raise RuntimeError('MAVEN_GPG_PASSPHRASE is not available')
    for row in rows:
        module = ROOT / row['module']
        pom = ET.parse(module / 'pom.xml').getroot()
        aid, version = row['artifactId'], row['version']
        assert version == VERSION
        packaging = pom.findtext('m:packaging', default='jar', namespaces=NS)
        batch = ('versioner' if aid == 'versioner' else 'boms' if packaging == 'pom'
                 else 'services' if '/services/' in module.as_posix() else 'modules')
        relative = Path(row['groupId'].replace('.', '/')) / aid / version
        stem = aid + '-' + version
        # Maven's installed POM is the consumer/flattened POM for shaded artifacts.
        # POM-packaged BOMs retain their build and dependency-management metadata.
        source_pom = module / 'pom.xml' if packaging == 'pom' else local / relative / (stem + '.pom')
        published_pom = ET.parse(source_pom).getroot()
        assert published_pom.findtext('m:artifactId', namespaces=NS) == aid
        assert published_pom.findtext('m:version', namespaces=NS) == version
        inputs = [(source_pom, stem + '.pom')]
        if packaging != 'pom':
            for suffix in ('.jar', '-sources.jar', '-javadoc.jar'):
                artifact = module / 'target' / (stem + suffix)
                assert artifact.is_file(), artifact
                assert digest(artifact) == digest(local / relative / artifact.name), artifact
                inputs.append((artifact, artifact.name))
        manifest[batch]['components'].append(row)
        for source, name in inputs:
            destination = OUT / batch / relative / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            signature = Path(str(destination) + '.asc')
            signed = subprocess.run(['gpg', '--batch', '--yes', '--pinentry-mode', 'loopback',
                                     '--passphrase-fd', '0', '--armor', '--detach-sign',
                                     '--output', str(signature), str(destination)],
                                    input=passphrase + '\n', capture_output=True, text=True)
            if signed.returncode:
                raise RuntimeError('GPG signing failed for ' + name + ': ' + signed.stderr)
            verified = subprocess.run(['gpg', '--batch', '--verify', str(signature), str(destination)],
                                      capture_output=True, text=True)
            if verified.returncode:
                raise RuntimeError('GPG verification failed for ' + name)
            for algorithm in ('md5', 'sha1', 'sha256'):
                Path(str(destination) + '.' + algorithm).write_text(digest(destination, algorithm), encoding='ascii')
            manifest[batch]['files'].append({'path': (relative / name).as_posix(),
                                             'sha256': digest(destination), 'source': str(source)})
        print('Prepared', batch, row['groupId'] + ':' + aid + ':' + version, flush=True)
    for batch, info in manifest.items():
        bundle = OUT / (batch + '.zip')
        with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
            for path in sorted((OUT / batch).rglob('*')):
                if path.is_file():
                    archive.write(path, path.relative_to(OUT / batch).as_posix())
        assert bundle.stat().st_size < 1_000_000_000
        info.update(bundle=str(bundle), sha256=digest(bundle), bytes=bundle.stat().st_size)
        print(batch, len(info['components']), 'components;', round(info['bytes'] / 1024**2, 1), 'MiB', flush=True)
    save(manifest_path, manifest)


def state_file(batch):
    return OUT / (batch + '-deployment.json')


def status(batch):
    state = json.loads(state_file(batch).read_text())
    request = urllib.request.Request(API + 'status?' + urllib.parse.urlencode({'id': state['deploymentId']}),
                                     data=b'', headers={'Authorization': authorization()}, method='POST')
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.load(response)
    state['status'] = result
    save(state_file(batch), state)
    print(json.dumps({k: v for k, v in result.items() if k != 'purls'}), flush=True)
    return result


def upload(batch):
    if state_file(batch).exists():
        raise RuntimeError('Deployment already recorded; query status instead of uploading twice')
    info = json.loads((OUT / 'manifest.json').read_text())[batch]
    bundle = Path(info['bundle'])
    assert digest(bundle) == info['sha256'], 'Prepared bundle changed'
    if batch != 'versioner':
        assert status('versioner')['deploymentState'] == 'PUBLISHED', 'Versioner is not published'
    if batch in ('services', 'modules'):
        bom_state = status('boms')['deploymentState']
        assert bom_state in ('PUBLISHING', 'PUBLISHED'), 'BOMs have not passed validation'
        # Installed consumer POMs are flattened: Central needs no remote parent
        # or imported BOM to validate these bundles. Overlap the publication queues.
        for item in info['files']:
            if item['path'].endswith('.pom'):
                pom_path = OUT / batch / item['path']
                assert digest(pom_path) == item['sha256']
                pom = ET.parse(pom_path).getroot()
                assert pom.find('m:parent', NS) is None
                assert not pom.findall('m:dependencyManagement/m:dependencies/m:dependency[m:scope="import"]', NS)
                assert all(pom.find('m:' + key, NS) is not None for key in ('name', 'description', 'url', 'licenses', 'developers', 'scm'))
    for row in info['components']:
        path = row['groupId'].replace('.', '/') + '/' + row['artifactId'] + '/' + row['version'] + '/' + row['artifactId'] + '-' + row['version'] + '.pom'
        try:
            with urllib.request.urlopen(urllib.request.Request(CENTRAL + path, method='HEAD'), timeout=30):
                raise RuntimeError('Coordinate is already published: ' + path)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
    boundary = 'GuicedEE' + uuid.uuid4().hex
    prefix = ('--' + boundary + '\r\nContent-Disposition: form-data; name="bundle"; filename="' + bundle.name
              + '"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()
    suffix = ('\r\n--' + boundary + '--\r\n').encode()
    connection = http.client.HTTPSConnection('central.sonatype.com', timeout=300)
    name = 'guicedee-' + VERSION + '-' + batch
    path = '/api/v1/publisher/upload?' + urllib.parse.urlencode({'name': name, 'publishingType': 'AUTOMATIC'})
    connection.putrequest('POST', path)
    connection.putheader('Authorization', authorization())
    connection.putheader('User-Agent', 'GuicedEE-Release/' + VERSION)
    connection.putheader('Content-Type', 'multipart/form-data; boundary=' + boundary)
    connection.putheader('Content-Length', str(len(prefix) + bundle.stat().st_size + len(suffix)))
    connection.endheaders()
    connection.send(prefix)
    with bundle.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            connection.send(chunk)
    connection.send(suffix)
    response = connection.getresponse()
    body = response.read().decode()
    if response.status != 201:
        raise RuntimeError('Central upload returned ' + str(response.status) + ': ' + body)
    deployment_id = str(uuid.UUID(body.strip()))
    save(state_file(batch), {'deploymentId': deployment_id, 'name': name, 'bundleSha256': info['sha256']})
    print('Uploaded', batch, deployment_id, flush=True)
    connection.close()


def verify(batch):
    info = json.loads((OUT / 'manifest.json').read_text())[batch]
    def check(item):
        with urllib.request.urlopen(CENTRAL + item['path'], timeout=120) as response:
            value = hashlib.file_digest(response, 'sha256').hexdigest()
        assert value == item['sha256'], 'Published bytes differ: ' + item['path']
        return {'path': item['path'], 'sha256': value, 'matches': True}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(check, info['files']))
    save(OUT / (batch + '-verified.json'), results)
    print('Verified from Central:', batch, len(info['components']), 'components,', len(results), 'files', flush=True)


def publish_batch(batch):
    if not state_file(batch).exists():
        upload(batch)
    deadline = time.monotonic() + 7200
    while time.monotonic() < deadline:
        result = status(batch)
        state = result['deploymentState']
        if state == 'PUBLISHED':
            # Allow a short CDN propagation delay before checking public bytes.
            for attempt in range(5):
                try:
                    verify(batch)
                    return
                except urllib.error.HTTPError as error:
                    if error.code not in (404, 429, 502, 503, 504) or attempt == 4:
                        raise
                    time.sleep(30)
        if state in ('FAILED', 'VALIDATED'):
            raise RuntimeError('Publication needs attention: ' + batch + ': ' + json.dumps(result))
        time.sleep(45)
    raise RuntimeError('Publication is still pending after two hours; resume with the recorded deployment ID')


def run():
    publish_batch('versioner')
    if not state_file('boms').exists():
        upload('boms')
    while True:
        state = status('boms')['deploymentState']
        if state in ('PUBLISHING', 'PUBLISHED'):
            break
        if state == 'FAILED':
            raise RuntimeError('BOM validation failed; downstream batches were not uploaded')
        time.sleep(45)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        jobs = [pool.submit(publish_batch, batch) for batch in ('boms', 'services', 'modules')]
        for job in concurrent.futures.as_completed(jobs):
            job.result()
    print('All 116 GuicedEE release components are published and verified from Central.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'run', 'upload', 'status', 'verify'))
    parser.add_argument('batch', choices=BATCHES, nargs='?')
    args = parser.parse_args()
    if args.command in ('prepare', 'run'):
        globals()[args.command]()
    elif not args.batch:
        parser.error('batch is required')
    else:
        globals()[args.command](args.batch)
