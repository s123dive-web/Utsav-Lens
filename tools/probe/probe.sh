#!/usr/bin/env bash
# Prints file listings (path, bytes, sha256 for LFS files) of candidate Hugging Face repositories.
set -u
repos=(
  "TheyCallMeHex/LCM-Dreamshaper-V7-ONNX"
  "onnx-community/stable-diffusion-v1-5-ONNX"
  "nmkd/stable-diffusion-1.5-onnx-fp16"
  "aislamov/lcm-dreamshaper-v7-onnx"
  "SimianLuo/LCM_Dreamshaper_v7"
)
for r in "${repos[@]}"; do
  code=$(curl -s -o /tmp/tree.json -w '%{http_code}' "https://huggingface.co/api/models/$r/tree/main?recursive=true")
  lic=$(curl -s "https://huggingface.co/api/models/$r" | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin); print(d.get("cardData",{}).get("license"), d.get("sha","")[:12], "gated=",d.get("gated"))
except Exception as e: print("?")')
  echo "## $r http=$code license/sha: $lic"
  [ "$code" = "200" ] && python3 - <<'PY'
import json
for f in json.load(open("/tmp/tree.json")):
    if f.get("type") != "file": continue
    p = f["path"]
    if not (p.endswith((".onnx", ".onnx_data", ".json", ".txt", ".pb", ".bin")) or "onnx" in p): continue
    lfs = f.get("lfs") or {}
    print(f'{p}\t{lfs.get("size", f.get("size"))}\t{lfs.get("oid","-")}')
PY
done
