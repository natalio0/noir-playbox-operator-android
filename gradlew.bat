\
    @ECHO OFF
    SET DIR=%~dp0
    IF NOT EXIST "%DIR%gradle\wrapper\gradle-wrapper.jar" (
      ECHO gradle-wrapper.jar belum ada. Buka project di Android Studio dan Sync terlebih dahulu.
      EXIT /B 1
    )
    java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
