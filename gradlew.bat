@rem Gradle wrapper script for Windows
@setlocal
@set DIRNAME=%~dp0
@set JAVA_HOME=C:\JDK17\jdk-17.0.13+11
@"%JAVA_HOME%\bin\java.exe" -cp "%DIRNAME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
@endlocal
