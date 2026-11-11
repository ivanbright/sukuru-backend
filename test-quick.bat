@echo off
REM ============================================
REM Quick Test Runner (Unit Tests Only)
REM ============================================

echo Running quick unit tests (no Docker required)...
echo.

call mvnw.cmd test -Dtest="!*IntegrationTest" -DfailIfNoTests=false

if errorlevel 1 (
    echo.
    echo Unit tests FAILED!
    exit /b 1
)

echo.
echo ============================================
echo Unit tests PASSED ✓
echo ============================================

exit /b 0
