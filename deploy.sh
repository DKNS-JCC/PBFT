#!/bin/bash
# deploy.sh — Clona PBFT desde GitHub, descarga Tomcat 9, compila y despliega.
#
# Uso:  ./deploy.sh [NODE_ID] [URLS_NODOS_SEPARADAS_POR_COMA]
# Ej.:  ./deploy.sh 0 "http://192.168.1.10:8080/PBFT/rest,http://192.168.1.11:8080/PBFT/rest,http://192.168.1.12:8080/PBFT/rest"
#
# NODE_ID: 0 = primera máquina (procesos 0,1)
#          1 = segunda máquina (procesos 2,3)
#          2 = tercera máquina  (procesos 4,5)

set -euo pipefail

# ── Configuración ──────────────────────────────────────────────────────────────
REPO_URL="https://github.com/DKNS-JCC/PBFT.git"
TOMCAT_VERSION="9.0.102"   # Cambia si hay versión más nueva en tomcat.apache.org
INSTALL_DIR="$(pwd)/pbft-deploy"

# ── Colores ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()   { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
title() { echo -e "\n${BLUE}══════════════════════════════════════${NC}\n${BLUE}  $1${NC}\n${BLUE}══════════════════════════════════════${NC}"; }

# ══════════════════════════════════════════════════════════════════════════════
# 1. Verificar requisitos
# ══════════════════════════════════════════════════════════════════════════════
title "1/6  Verificando requisitos"

# Java 17+
if ! command -v java &>/dev/null; then
    error "Java no encontrado. Instala Java 17:\n  Ubuntu/Debian : sudo apt install openjdk-17-jdk\n  Fedora/RHEL   : sudo dnf install java-17-openjdk-devel"
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[ "$JAVA_VER" -ge 17 ] || error "Se requiere Java 17+. Versión actual: $JAVA_VER"
log "Java $JAVA_VER  ✓"

command -v javac &>/dev/null || error "javac no encontrado. Instala el JDK completo (no solo el JRE)."
log "javac  ✓"

command -v jar &>/dev/null   || error "jar no encontrado. Instala el JDK completo."
log "jar    ✓"

command -v git &>/dev/null   || error "git no encontrado. Instala git: sudo apt install git"
log "git    ✓"

( command -v curl &>/dev/null || command -v wget &>/dev/null ) \
    || error "Se necesita curl o wget para descargar Tomcat."

( command -v python3 &>/dev/null ) \
    || error "Se necesita python3 para parchear la configuración del nodo."
log "python3  ✓"

# ══════════════════════════════════════════════════════════════════════════════
# 2. Configuración del nodo (interactiva si no se pasan argumentos)
# ══════════════════════════════════════════════════════════════════════════════
title "2/6  Configuración del nodo"

NODE_ID=${1:-""}
NODE_URLS=${2:-""}

if [ -z "$NODE_ID" ]; then
    echo "Cada máquina gestiona 2 procesos. Con 3 máquinas hay 6 procesos en total."
    read -rp "  ID de este nodo [0 / 1 / 2]: " NODE_ID
fi
[[ "$NODE_ID" =~ ^[0-2]$ ]] || error "NODE_ID debe ser 0, 1 o 2."

if [ -z "$NODE_URLS" ]; then
    echo ""
    echo "Introduce las URLs base de los 3 nodos separadas por coma."
    echo "  Ejemplo: http://192.168.1.10:8080/PBFT/rest,http://192.168.1.11:8080/PBFT/rest,http://192.168.1.12:8080/PBFT/rest"
    echo "  (Para prueba local usa: http://localhost:8080/PBFT/rest)"
    read -rp "  URLs de nodos: " NODE_URLS
fi

# La URL del cliente es la URL del nodo 0 (primer campo)
CLIENT_URL=$(echo "$NODE_URLS" | cut -d',' -f1 | xargs)

log "Node ID    : $NODE_ID"
log "Nodos      : $NODE_URLS"
log "Cliente URL: $CLIENT_URL"

# ══════════════════════════════════════════════════════════════════════════════
# 3. Clonar / actualizar repositorio
# ══════════════════════════════════════════════════════════════════════════════
title "3/6  Repositorio"

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

if [ -d "PBFT/.git" ]; then
    warn "Repositorio ya existe. Actualizando con git pull..."
    git -C PBFT pull
else
    log "Clonando $REPO_URL ..."
    git clone "$REPO_URL"
fi

PROJECT_DIR="$INSTALL_DIR/PBFT"
log "Repositorio listo en $PROJECT_DIR"

# ══════════════════════════════════════════════════════════════════════════════
# 4. Descargar Apache Tomcat 9
# ══════════════════════════════════════════════════════════════════════════════
title "4/6  Apache Tomcat $TOMCAT_VERSION"

TOMCAT_DIR="$INSTALL_DIR/tomcat"

if [ -f "$TOMCAT_DIR/bin/startup.sh" ]; then
    warn "Tomcat ya instalado en $TOMCAT_DIR — omitiendo descarga."
else
    TOMCAT_URL="https://downloads.apache.org/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
    TOMCAT_TMP="/tmp/apache-tomcat-${TOMCAT_VERSION}.tar.gz"

    log "Descargando $TOMCAT_URL ..."
    if command -v curl &>/dev/null; then
        curl -fSL "$TOMCAT_URL" -o "$TOMCAT_TMP"
    else
        wget -q "$TOMCAT_URL" -O "$TOMCAT_TMP"
    fi

    mkdir -p "$TOMCAT_DIR"
    tar -xzf "$TOMCAT_TMP" -C "$TOMCAT_DIR" --strip-components=1
    rm "$TOMCAT_TMP"
    chmod +x "$TOMCAT_DIR/bin/"*.sh
    log "Tomcat instalado en $TOMCAT_DIR"
fi

# ══════════════════════════════════════════════════════════════════════════════
# 5. Parchear configuración del nodo en Servicio.java
# ══════════════════════════════════════════════════════════════════════════════
title "5/6  Parcheando Servicio.java (nodo $NODE_ID)"

SERVICIO="$PROJECT_DIR/src/obligatoria/Servicio.java"

python3 - <<PYEOF
import re

with open("$SERVICIO", "r", encoding="utf-8") as f:
    src = f.read()

# Parchear nodoId
src = re.sub(r'private int nodoId\s*=\s*\d+', 'private int nodoId = $NODE_ID', src)

# Parchear array de nodos
urls = [u.strip() for u in "$NODE_URLS".split(",") if u.strip()]
java_urls = ",\n            ".join(f'"{u}"' for u in urls)
new_block = f'nodos = new String[] {{\n            {java_urls}\n        }};'
src = re.sub(
    r'nodos\s*=\s*new String\[\]\s*\{[^}]*\};',
    new_block,
    src,
    flags=re.DOTALL
)

# Parchear clienteUrl del constructor
src = re.sub(r'clienteUrl\s*=\s*"[^"]*";', f'clienteUrl = "$CLIENT_URL";', src)

with open("$SERVICIO", "w", encoding="utf-8") as f:
    f.write(src)

print("  Servicio.java parcheado OK")
PYEOF

# ══════════════════════════════════════════════════════════════════════════════
# 6. Compilar → empaquetar WAR → desplegar → arrancar Tomcat
# ══════════════════════════════════════════════════════════════════════════════
title "6/6  Compilar, empaquetar y desplegar"

LIB_DIR="$PROJECT_DIR/WebContent/WEB-INF/lib"
CLASSES_DIR="$PROJECT_DIR/WebContent/WEB-INF/classes"
SRC_DIR="$PROJECT_DIR/src"
WAR="$INSTALL_DIR/PBFT.war"

# Compilar
mkdir -p "$CLASSES_DIR"
CLASSPATH=$(find "$LIB_DIR" -name "*.jar" | tr '\n' ':')
find "$SRC_DIR" -name "*.java" > /tmp/pbft_sources.txt
log "Compilando fuentes Java..."
javac --release 17 -classpath "$CLASSPATH" -d "$CLASSES_DIR" @/tmp/pbft_sources.txt
rm /tmp/pbft_sources.txt
log "Compilación exitosa."

# Empaquetar WAR
log "Generando PBFT.war..."
cd "$PROJECT_DIR/WebContent"
jar -cf "$WAR" .
cd "$INSTALL_DIR"
log "WAR generado: $WAR"

# Detener Tomcat si estaba corriendo
if pgrep -f "catalina" &>/dev/null; then
    warn "Tomcat en ejecución. Deteniéndolo..."
    "$TOMCAT_DIR/bin/shutdown.sh" 2>/dev/null || true
    sleep 3
fi

# Desplegar WAR
log "Desplegando WAR en Tomcat..."
rm -rf "$TOMCAT_DIR/webapps/PBFT" "$TOMCAT_DIR/webapps/PBFT.war"
cp "$WAR" "$TOMCAT_DIR/webapps/"

# Arrancar Tomcat
log "Iniciando Tomcat..."
CATALINA_HOME="$TOMCAT_DIR" "$TOMCAT_DIR/bin/startup.sh"

# ══════════════════════════════════════════════════════════════════════════════
echo -e "\n${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Despliegue completado — Nodo $NODE_ID                        ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║${NC}  Estado:  http://localhost:8080/PBFT/rest/servicio/estado  ${GREEN}║${NC}"
echo -e "${GREEN}║${NC}  Logs:    tail -f $TOMCAT_DIR/logs/catalina.out   ${GREEN}║${NC}"
echo -e "${GREEN}║${NC}  Parar:   $TOMCAT_DIR/bin/shutdown.sh            ${GREEN}║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
