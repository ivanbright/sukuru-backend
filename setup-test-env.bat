@echo off
REM ============================================
REM Test Environment Setup
REM ============================================

echo ============================================
echo Setting Up Test Environment
echo ============================================
echo.

echo [1/3] Checking prerequisites...
echo.

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java 21 is not installed or not in PATH
    echo Please install Java 21 and try again
    exit /b 1
)
echo Java: OK ✓

REM Check Maven
call mvnw.cmd --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven wrapper is not working
    exit /b 1
)
echo Maven: OK ✓

REM Check Docker
docker info >nul 2>&1
if errorlevel 1 (
    echo WARNING: Docker is not running
    echo Integration tests will be skipped
    echo Please start Docker Desktop if you want to run integration tests
    set DOCKER_AVAILABLE=false
) else (
    echo Docker: OK ✓
    set DOCKER_AVAILABLE=true
)
echo.

echo [2/3] Installing dependencies...
call mvnw.cmd dependency:resolve dependency:resolve-plugins
if errorlevel 1 (
    echo ERROR: Failed to download dependencies
    exit /b 1
)
echo Dependencies installed ✓
echo.

echo [3/3] Verifying project compilation...
call mvnw.cmd clean compile test-compile
if errorlevel 1 (
    echo ERROR: Project compilation failed
    exit /b 1
)
echo Compilation successful ✓
echo.

echo ============================================
echo Test Environment Ready!
echo ============================================
echo.
echo Next steps:
echo   - Run quick tests: test-quick.bat
echo   - Run all tests: test-all.bat
if "%DOCKER_AVAILABLE%"=="false" (
    echo.
    echo Note: Docker is not running.
    echo Start Docker Desktop to enable integration tests.
)
echo ============================================

exit /b 0
