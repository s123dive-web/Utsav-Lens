#!/usr/bin/env bash
# Downloads the Local AI model files into ./model and prints size + SHA-256 of each file.
# The same files and checksums are pinned in engine-local's LocalModelManifest.
set -euo pipefail
base="https://huggingface.co/SimianLuo/LCM_Dreamshaper_v7/resolve/main"
files=(
  tokenizer/vocab.json tokenizer/merges.txt
  text_encoder/model.onnx
  unet/model.onnx unet/model.onnx_data
  vae_encoder/model.onnx vae_decoder/model.onnx
)
for f in "${files[@]}"; do
  mkdir -p "model/$(dirname "$f")"
  curl -fsSL --retry 5 --retry-delay 5 -C - -o "model/$f" "$base/$f"
  echo "SD-FILE $f $(stat -c %s "model/$f") $(sha256sum "model/$f" | cut -d' ' -f1)"
done
