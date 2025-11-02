@echo off
REM Script to push trade-engine project to GitHub (Windows Batch)

REM Add all files
git add .

REM Commit with message
git commit -m "Initial commit: Trade Engine Spring Boot application"

REM Push to GitHub - try main branch first
git push -u origin main
if errorlevel 1 (
    echo Trying master branch...
    git push -u origin master
)

