@echo off
REM ============================================
REM Complete Test Suite Runner
REM Following DevOps Best Practices
REM ============================================

echo ============================================
echo Starting Complete Test Suite
echo ============================================
echo.

REM Set environment variables
set MAVEN_OPTS=-Xmx1024m
set DOCKER_REQUIRED=true

echo [1/5] Checking Docker availability...
docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not running. Tests require Docker for Testcontainers.
    echo Please start Docker Desktop and try again.
    exit /b 1
)
echo Docker is running ✓
echo.

echo [2/5] Running unit tests...
call mvnw.cmd clean test -Dtest="!*IntegrationTest" -DfailIfNoTests=false
if errorlevel 1 (
    echo ERROR: Unit tests failed!
    exit /b 1
)
echo Unit tests passed ✓
echo.

echo [3/5] Running integration tests...
call mvnw.cmd verify -Dtest="*IntegrationTest" -DfailIfNoTests=false
if errorlevel 1 (
    echo ERROR: Integration tests failed!
    exit /b 1
)
echo Integration tests passed ✓
echo.

echo [4/5] Generating code coverage report...
call mvnw.cmd jacoco:report
if errorlevel 1 (
    echo WARNING: Code coverage report generation failed
) else (
    echo Code coverage report generated ✓
    echo Report location: target\site\jacoco\index.html
)
echo.

echo [5/5] Running security vulnerability scan...
call mvnw.cmd dependency-check:check
if errorlevel 1 (
    echo WARNING: Security vulnerabilities detected - check report
    echo Report location: target\dependency-check-report.html
) else (
    echo Security scan completed ✓
)
echo.

echo ============================================
echo Test Suite Summary
echo ============================================
echo Unit Tests: PASSED
echo Integration Tests: PASSED
echo Code Coverage: See target\site\jacoco\index.html
echo Security Scan: See target\dependency-check-report.html
echo ============================================
echo.
echo To view reports:
echo   - Coverage: start target\site\jacoco\index.html
echo   - Security: start target\dependency-check-report.html
echo ============================================

exit /b 0
