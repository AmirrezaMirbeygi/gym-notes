# Get SHA-1 fingerprint for Google Sign-In
# Run this in Android Studio's terminal

$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"

if (Test-Path $debugKeystore) {
    Write-Host "Found debug keystore at: $debugKeystore" -ForegroundColor Green
    Write-Host ""
    Write-Host "Getting SHA-1 fingerprint..." -ForegroundColor Yellow
    Write-Host ""
    
    # Try to find keytool in common Android Studio JDK locations
    $keytoolPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Android\Android Studio\jre\bin\keytool.exe",
        "$env:JAVA_HOME\bin\keytool.exe"
    )
    
    $keytool = $null
    foreach ($path in $keytoolPaths) {
        if (Test-Path $path) {
            $keytool = $path
            break
        }
    }
    
    if ($keytool -eq $null) {
        Write-Host "ERROR: Could not find keytool." -ForegroundColor Red
        Write-Host ""
        Write-Host "Please run this in Android Studio's terminal (View -> Tool Windows -> Terminal)" -ForegroundColor Yellow
        Write-Host "Or manually find keytool.exe in your Android Studio installation" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host "Using keytool at: $keytool" -ForegroundColor Cyan
    Write-Host ""
    
    & $keytool -list -v -keystore $debugKeystore -alias androiddebugkey -storepass android -keypass android | Select-String "SHA1:"
    
    Write-Host ""
    Write-Host "Copy the SHA1 value above (the part after 'SHA1:')" -ForegroundColor Green
} else {
    Write-Host "ERROR: Debug keystore not found at: $debugKeystore" -ForegroundColor Red
    Write-Host "Please build your app first in Android Studio to generate the keystore." -ForegroundColor Yellow
}


