#!/bin/sh
# Linux/Mac用:Voskの英語モデルをダウンロードして assets に配置する
set -e
curl -L -o model.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip -q model.zip
mkdir -p app/src/main/assets
rm -rf app/src/main/assets/model-en-us
mv vosk-model-small-en-us-0.15 app/src/main/assets/model-en-us
echo "small-en-us-0.15" > app/src/main/assets/model-en-us/uuid
rm model.zip
echo "完了: app/src/main/assets/model-en-us"
