#!/usr/bin/env bash
# Research helper: prints the exact REST examples from the official Gemini guides.
set -u
curl -fsSL https://ai.google.dev/gemini-api/docs/image-generation.md.txt -o /tmp/guide.txt 2>/dev/null || { echo "(guide not reachable)"; exit 0; }
echo "## image guide: REST text-to-image (184-204)"; sed -n '184,204p' /tmp/guide.txt | cut -c1-200
echo "## image guide: REST edit with image (412-440)"; sed -n '412,440p' /tmp/guide.txt | cut -c1-200
echo "## image guide: jq / outputs lines"; grep -n -E 'jq|outputs|output_image|"status"|error' /tmp/guide.txt | cut -c1-200 | head -12
if curl -fsSL https://ai.google.dev/gemini-api/docs/interactions.md.txt -o /tmp/ia.txt 2>/dev/null; then
  echo "## interactions guide: first REST example and response fields"
  n=$(grep -n -m1 'curl ' /tmp/ia.txt | cut -d: -f1); [ -n "$n" ] && sed -n "${n},$((n+14))p" /tmp/ia.txt | cut -c1-200
  grep -n -E '"outputs"|"type": "(text|image)"|"text":|jq|"status"|"usage"|safety|blocked|finish' /tmp/ia.txt | cut -c1-200 | awk '!seen[substr($0, index($0,":")+1)]++' | head -22
else
  echo "(interactions guide not reachable)"
fi
