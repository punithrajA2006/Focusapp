#!/bin/bash
# FocusLock GitHub Push Helper Script
# Usage: ./push_to_github.sh [optional-repo-url]

set -e

echo "============================================="
echo "  FocusLock -> GitHub Sync Helper"
echo "============================================="

# Ensure git is initialized
if [ ! -d ".git" ]; then
    echo "[*] Initializing local git repository..."
    git init
    git branch -M main
fi

# Stage and commit any unstaged changes
echo "[*] Checking git status..."
git add .
if git diff-index --quiet HEAD --; then
    echo "[+] Workspace is clean, nothing new to commit."
else
    echo "[*] Committing changes..."
    git commit -m "feat: complete FocusLock study enforcement app and Fedora companion"
fi

# If repo URL was provided as an argument
REPO_URL="$1"

if [ -z "$REPO_URL" ]; then
    # Check if gh CLI is available and logged in
    if command -v gh &> /dev/null && gh auth status &> /dev/null; then
        echo ""
        echo "[?] GitHub CLI (gh) detected!"
        read -p "Create a new private or public repo automatically? [y/N]: " USE_GH
        if [[ "$USE_GH" =~ ^[Yy]$ ]]; then
            read -p "Repository name [default: focuslock]: " GH_NAME
            GH_NAME=${GH_NAME:-focuslock}
            read -p "Visibility (public/private) [default: public]: " GH_VIS
            GH_VIS=${GH_VIS:-public}
            gh repo create "$GH_NAME" --"$GH_VIS" --source=. --remote=origin --push
            echo "[SUCCESS] Pushed to https://github.com/$(gh api user -q .login)/$GH_NAME"
            exit 0
        fi
    fi

    # Prompt user for remote URL
    echo ""
    echo "Please provide your GitHub repository URL."
    echo "Examples:"
    echo "  HTTPS: https://github.com/<your-username>/focuslock.git"
    echo "  SSH:   git@github.com:<your-username>/focuslock.git"
    echo ""
    read -p "Enter GitHub URL: " REPO_URL
fi

if [ -z "$REPO_URL" ]; then
    echo "[-] No repository URL provided. Exiting."
    exit 1
fi

# Set origin
if git remote | grep -q "^origin$"; then
    echo "[*] Updating existing remote origin to $REPO_URL..."
    git remote set-url origin "$REPO_URL"
else
    echo "[*] Adding remote origin: $REPO_URL..."
    git remote add origin "$REPO_URL"
fi

# Push
echo "[*] Pushing branch 'main' to GitHub..."
git push -u origin main

echo ""
echo "============================================="
echo "  [SUCCESS] FocusLock successfully pushed to GitHub!"
echo "============================================="
