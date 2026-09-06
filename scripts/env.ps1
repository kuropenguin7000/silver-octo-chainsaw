# Point this shell at JDK 25 for the kessai build.
#
# This machine's global JAVA_HOME is JDK 8 (C:\DEV_HOME\TOOLS\java\1.8.0_111) because other work
# on this box needs it -- do not change it globally. Two things break without this script:
#   1. The gradlew launcher itself runs on JAVA_HOME, and Gradle 9 refuses to start on JDK 8.
#   2. The JDK 8 truststore lacks the corporate TLS-interception root CA, so downloads fail
#      with "PKIX path building failed". Zulu 25's truststore has it.
#
# Usage (dot-source it, so it affects the current shell):
#   . .\scripts\env.ps1

$env:JAVA_HOME = "C:\DEV_HOME\TOOLS\java\zulu25.36.205-ca-jdk25.0.4.1-win_x64"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME -> $env:JAVA_HOME"
java -version
