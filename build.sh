#!/bin/bash
# build.sh — compila el proyecto y genera dist/PBFT.war + dist/cliente/
#
# Uso: ./build.sh
# Requiere: JDK 8+ instalado (sudo apt install default-jdk)

set -euo pipefail

# Localizar JDK
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JDK_HOME="$JAVA_HOME"
elif command -v javac >/dev/null 2>&1; then
    JDK_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
else
    echo "ERROR: No se encontro JDK. Instala con: sudo apt install default-jdk" >&2
    exit 1
fi

JAVAC="$JDK_HOME/bin/javac"
JAR="$JDK_HOME/bin/jar"

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
LIB="$ROOT/WebContent/WEB-INF/lib"
CLASSES="$ROOT/WebContent/WEB-INF/classes"
DIST="$ROOT/dist"
CLIENT="$DIST/cliente"

# Limpiar
rm -rf "$CLASSES" "$DIST"
mkdir -p "$CLASSES" "$DIST" "$CLIENT/lib"

# Classpath con todos los jars
CP=$(find "$LIB" -name "*.jar" | tr '\n' ':')

# Compilar
echo "[1/3] Compilando..."
find "$SRC" -name "*.java" > /tmp/pbft_sources.txt
"$JAVAC" -classpath "$CP" -d "$CLASSES" @/tmp/pbft_sources.txt
rm /tmp/pbft_sources.txt

# Empaquetar WAR (servidor)
echo "[2/3] Generando PBFT.war..."
cd "$ROOT/WebContent"
"$JAR" -cf "$DIST/PBFT.war" .
cd "$ROOT"

# Empaquetar JAR cliente
echo "[3/3] Generando cliente..."
echo "Main-Class: obligatoria.Interfaz" > "$DIST/manifest.txt"
"$JAR" -cfm "$CLIENT/Interfaz.jar" "$DIST/manifest.txt" -C "$CLASSES" obligatoria/Interfaz.class
cp "$LIB"/*.jar "$CLIENT/lib/"

# run.sh para el cliente
cat > "$CLIENT/run.sh" <<'EOF'
#!/bin/bash
cd "$(dirname "$0")"
java -cp "Interfaz.jar:lib/*" obligatoria.Interfaz
EOF
chmod +x "$CLIENT/run.sh"

echo ""
echo "Listo. Resultado en $DIST"
echo "  - PBFT.war        -> copiar a <tomcat>/webapps/ en cada PC servidor"
echo "  - cliente/run.sh  -> ejecutar en el PC cliente"
