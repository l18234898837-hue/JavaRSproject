@echo off
setlocal
title RSImageProcess Backend (rebuilt)

set "PROJECT_ROOT=D:\Lwy_project_space\RSImageProcess"
set "BACKEND_ROOT=%PROJECT_ROOT%\JavaRSproject"
set "RUNTIME_ROOT=%PROJECT_ROOT%\runtime"

set "FILE_UPLOAD_PATH=%RUNTIME_ROOT%\file-uploads"
set "MULTISOURCE_WORK_ROOT=%RUNTIME_ROOT%\multisource-work"
set "MULTISOURCE_ARTIFACT_ROOT=%RUNTIME_ROOT%\multisource-artifacts"
set "MULTISOURCE_WORKER_COMMAND=%BACKEND_ROOT%\rs-algorithm-worker\.venv\Scripts\python.exe,-m,rs_algorithm_worker.cli"
set "MULTISOURCE_WORKER_TIMEOUT=30m"
set "MULTISOURCE_WORKER_PROCESSOR_VERSION=local-dev"

rem Prefer the checked-out worker source over any stale wheel in site-packages.
rem This keeps local restarts aligned with the current repository code.
set "PYTHONPATH=%BACKEND_ROOT%\rs-algorithm-worker\src;%PYTHONPATH%"

if not exist "%FILE_UPLOAD_PATH%" mkdir "%FILE_UPLOAD_PATH%"
if not exist "%MULTISOURCE_WORK_ROOT%" mkdir "%MULTISOURCE_WORK_ROOT%"
if not exist "%MULTISOURCE_ARTIFACT_ROOT%" mkdir "%MULTISOURCE_ARTIFACT_ROOT%"
if not exist "%PROJECT_ROOT%\runtime-tmp" mkdir "%PROJECT_ROOT%\runtime-tmp"

rem The assembled JAR is a Spring Boot executable archive with classes under BOOT-INF.
java -Djava.io.tmpdir="%PROJECT_ROOT%\runtime-tmp" -jar "%BACKEND_ROOT%\htht-admin\target\htht-admin.jar" ^
  --spring.profiles.active=dev ^
  --spring.flyway.out-of-order=true ^
  --spring.flyway.password=845420 ^
  --spring.datasource.dynamic.datasource.postgres.password=845420 ^
  --MULTISOURCE_WORKER_COMMAND="%MULTISOURCE_WORKER_COMMAND%"

echo.
echo Backend process exited with code %ERRORLEVEL%.
endlocal
