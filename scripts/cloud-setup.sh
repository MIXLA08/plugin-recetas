#!/usr/bin/env bash
# Preparación reproducible del entorno cloud; no modifica código ni dependencias.
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR=/workspace/tools
export JAVA_HOME="$TOOLS_DIR/jdk-25.0.4.1+1"
export GRADLE_USER_HOME=/workspace/.gradle
mkdir -p "$TOOLS_DIR" "$GRADLE_USER_HOME"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    SETUP_TMP=$(mktemp -d /tmp/recetas-java.XXXXXX)
    trap 'rm -rf -- "$SETUP_TMP"' EXIT
    curl --fail --location --retry 2 \
      'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz' \
      --output "$SETUP_TMP/java.tar.gz"
    (cd "$SETUP_TMP" && echo 'dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e  java.tar.gz' | sha256sum --check -)
    tar -xzf "$SETUP_TMP/java.tar.gz" -C "$TOOLS_DIR"
fi
export PATH="$JAVA_HOME/bin:$PATH"
# Gradle/Java no interpreta HTTPS_PROXY automáticamente. Configuración local,
# sin credenciales ni desactivar TLS. Conservar las propiedades ajenas.
python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlsplit
path = Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
existing = path.read_text().splitlines() if path.exists() else []
proxy = urlsplit(os.environ.get('HTTPS_PROXY', ''))
props = {'org.gradle.java.installations.paths': os.environ['JAVA_HOME'], 'org.gradle.workers.max': '2'}
if proxy.hostname:
    for scheme in ['https', 'http']:
        props[f'systemProp.{scheme}.proxyHost'] = proxy.hostname
        props[f'systemProp.{scheme}.proxyPort'] = str(proxy.port or 80)
if Path('/etc/ssl/certs/java/cacerts').exists():
    props['systemProp.javax.net.ssl.trustStore'] = '/etc/ssl/certs/java/cacerts'
lines = [line for line in existing if line.split('=', 1)[0].strip() not in props]
path.write_text('\n'.join(lines + [f'{k}={v}' for k, v in props.items()]) + '\n')
PY
java -version
cd "$PROJECT_DIR"
./gradlew --no-daemon clean build
