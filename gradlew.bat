@rem Gradle wrapper script for Windows
@setlocal
@set DIRNAME=%~dp0
@set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
@"%JAVA_HOME%\bin\java.exe" -cp "%DIRNAME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
@endlocal
