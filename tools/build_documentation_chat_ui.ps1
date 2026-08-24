# cesta k Angular aplikaci - musi byt lokalne k dispozici
$ProjectPath = 'C:\VA\Projects\VA\documentation-chat\documentation-chat-ui'
$BrowserOutputPath = Join-Path $ProjectPath 'dist\documentation-chat-ui\browser'
$DocumentationAssetsPath = Join-Path $PSScriptRoot '..\docs\assets'
$ProfileAssetsPath = Join-Path $DocumentationAssetsPath 'profiles'

$ErrorActionPreference = 'Stop'
npm --prefix $ProjectPath run build
if ($LASTEXITCODE) { throw "Build failed with exit code $LASTEXITCODE." }
New-Item -ItemType Directory -Path (Join-Path $DocumentationAssetsPath 'documentation-chat-ui') -Force | Out-Null
New-Item -ItemType Directory -Path $ProfileAssetsPath -Force | Out-Null
Copy-Item (Join-Path $BrowserOutputPath 'main.js') (Join-Path $DocumentationAssetsPath 'documentation-chat-ui\documentation-chat-ui.js') -Force
Copy-Item (Join-Path $BrowserOutputPath 'assets\profiles\*') $ProfileAssetsPath -Recurse -Force
