#!/bin/bash
# Script to push trade-engine project to GitHub

# Add all files
git add .

# Commit with message
git commit -m "Initial commit: Trade Engine Spring Boot application"

# Push to GitHub
git push -u origin main

# If main branch doesn't exist, try master
if [ $? -ne 0 ]; then
    echo "Trying master branch..."
    git push -u origin master
fi

