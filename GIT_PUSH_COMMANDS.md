# Git Commands to Push to GitHub

## Quick One-Liner (Git Bash / PowerShell)

```bash
git add . && git commit -m "Initial commit: Trade Engine Spring Boot application" && git push -u origin main
```

## Step-by-Step Commands

### 1. Add all files to staging
```bash
git add .
```

### 2. Commit with a message
```bash
git commit -m "Initial commit: Trade Engine Spring Boot application"
```

### 3. Push to GitHub
```bash
# Try main branch first
git push -u origin main

# If main doesn't exist, use master
git push -u origin master
```

## Complete Command Sequence

Copy and paste this entire block:

```bash
git add .
git commit -m "Initial commit: Trade Engine Spring Boot application"
git branch -M main
git push -u origin main
```

## Alternative: If you need to create main branch

```bash
git add .
git commit -m "Initial commit: Trade Engine Spring Boot application"
git checkout -b main
git push -u origin main
```

## Troubleshooting

### If you get "fatal: refusing to merge unrelated histories"
```bash
git pull origin main --allow-unrelated-histories
# Or
git pull origin master --allow-unrelated-histories
```

### If the remote branch doesn't exist yet
```bash
git push -u origin main --force
# Use --force only if you're sure you want to overwrite remote
```

