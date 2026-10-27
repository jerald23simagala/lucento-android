#!/usr/bin/env bash
# Local JVM test harness: compiles the Android-free parts of Lucento (core/money, domain, repositories, Room
# entity/DAO declarations with annotation stubs, pure feature state builders) plus app/src/test, and runs JUnit.
# It is a fallback for environments without the Android SDK/Maven. CI (./gradlew testDebugUnitTest) is the
# authority; this harness proves the financial logic and tests compile and pass with Kotlin 2.0.21.
#
# usage: tools/jvm-harness/run.sh [kotlin-lib-dir]   (default: /opt/gradle-8.14.3/lib)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LIB="${1:-/opt/gradle-8.14.3/lib}"
OUT="${TMPDIR:-/tmp}/lucento-jvm-harness"
rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"
CP="$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$LIB/junit-4.13.2.jar:$(ls "$LIB"/hamcrest-core-*.jar 2>/dev/null | head -n1)"
KOTLINC_CP="$LIB/kotlin-compiler-embeddable-2.0.21.jar:$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlin-script-runtime-2.0.21.jar:$LIB/kotlin-reflect-2.0.21.jar:$LIB/kotlin-daemon-embeddable-2.0.21.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$(ls "$LIB"/trove4j*.jar 2>/dev/null | head -n1):$(ls "$LIB"/annotations-*.jar 2>/dev/null | head -n1)"
SRC="$ROOT/app/src/main/java/com/lucento/app"
# Android-free sources: everything under core/money, domain and data/repository, the Room entity/DAO declarations,
# plus the pure feature files listed in extra-sources.txt.
MAIN_FILES="$(find "$SRC/core/money" "$SRC/domain" "$SRC/data/repository" -name '*.kt' | sort | tr '\n' ' ') \
$(ls "$SRC"/data/local/*Entity.kt "$SRC"/data/local/*Dao.kt | tr '\n' ' ') \
$(grep -v '^#' "$ROOT/tools/jvm-harness/extra-sources.txt" | sed "s#^#$SRC/#" | tr '\n' ' ')"
kotlinc() { java -Xmx2g -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"; }
echo "Compiling main sources…"
kotlinc -module-name app -cp "$CP" -d "$OUT/main" $MAIN_FILES "$ROOT"/tools/jvm-harness/stubs/*.kt
echo "Compiling tests…"
TEST_FILES=$(find "$ROOT/app/src/test" -name '*.kt' | tr '\n' ' ')
kotlinc -module-name app -Xfriend-paths="$OUT/main" -cp "$CP:$OUT/main" -d "$OUT/test" $TEST_FILES
CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort | tr '\n' ' ')
echo "Running tests…"
java -cp "$CP:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES
