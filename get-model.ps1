# Windows用:Voskの英語モデルをダウンロードして assets に配置する
$ErrorActionPreference = "Stop"
$url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
$dest = "app\src\main\assets"
Invoke-WebRequest $url -OutFile model.zip
Expand-Archive model.zip -DestinationPath . -Force
New-Item -ItemType Directory -Force $dest | Out-Null
if (Test-Path "$dest\model-en-us") { Remove-Item -Recurse -Force "$dest\model-en-us" }
Move-Item vosk-model-small-en-us-0.15 "$dest\model-en-us"
Set-Content "$dest\model-en-us\uuid" "small-en-us-0.15"
Remove-Item model.zip
Write-Host "完了: $dest\model-en-us"
