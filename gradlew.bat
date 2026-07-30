@echo off
where gradle >nul 2>nul || (echo Gradle is not installed. Use GitHub Actions. & exit /b 1)
gradle %*
