# Script to push LiteRT-LM model to Android emulator
# Source: C:\Users\mac\AndroidStudioProjects\ext\gemma-3n-E4B-it-int4.litertlm
# Dest: /data/local/tmp/gemma-3n-E4B-it-int4.litertlm

$sourcePath = "C:\Users\mac\AndroidStudioProjects\ext\gemma-3n-E4B-it-int4.litertlm"
$destPath = "/data/local/tmp/"
$modelName = "gemma-3n-E4B-it-int4.litertlm"

Write-Host "Checking for source model file..."
if (!(Test-Path $sourcePath)) {
    Write-Error "Model file not found at: $sourcePath"
    exit 1
}

Write-Host "Checking for connected device..."
$deviceCheck = adb devices
if ($deviceCheck -notmatch "\tdevice") {
    Write-Error "No Android device/emulator found. Please start an emulator."
    exit 1
}

Write-Host "Pushing model to device (this may take a minute)..."
adb push "$sourcePath" "$destPath"

if ($LASTEXITCODE -eq 0) {
    Write-Host "Success! Model pushed to $destPath$modelName"
} else {
    Write-Error "Failed to push model."
}
