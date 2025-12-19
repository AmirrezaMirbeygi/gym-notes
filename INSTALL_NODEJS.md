# Install Node.js - Step by Step

## You Need Node.js First

Your terminal shows Node.js is not installed. Let's fix that!

## Installation Steps

### Option 1: Direct Download (Easiest)

1. **Go to**: https://nodejs.org/
2. **Download**: Click the big green "LTS" button (Long Term Support version)
   - This is usually v20.x.x or v18.x.x
   - LTS = stable, recommended for production
3. **Run the installer**:
   - Double-click the downloaded `.msi` file
   - Click "Next" through the installer
   - **Important**: Check "Add to PATH" (should be checked by default)
   - Click "Install"
4. **Restart your terminal**:
   - Close Android Studio terminal
   - Open a new terminal/PowerShell
   - Or restart Android Studio

### Option 2: Using Chocolatey (If you have it)

```bash
choco install nodejs-lts
```

## Verify Installation

After installing, open a **new terminal** and run:

```bash
node --version
```

You should see something like: `v20.11.0` or `v18.19.0`

Also verify npm (comes with Node.js):

```bash
npm --version
```

You should see something like: `10.2.4`

## After Node.js is Installed

Once Node.js works, we'll:
1. Install Firebase CLI: `npm install -g firebase-tools`
2. Set up Firebase project
3. Deploy the backend

## Troubleshooting

### "node is not recognized" after install
- **Restart your terminal** (close and reopen)
- **Restart Android Studio** if using its terminal
- Check if Node.js is in PATH:
  - Windows: Search "Environment Variables" in Start menu
  - Check if `C:\Program Files\nodejs\` is in PATH

### Still not working?
- Try installing again
- Make sure to check "Add to PATH" during installation
- Restart your computer if needed

## Next Steps

After Node.js is installed and verified:
1. Install Firebase CLI
2. Set up Firebase project
3. Deploy functions

Let me know when Node.js is installed and we'll continue! 🚀


