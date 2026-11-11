#!/usr/bin/env bash
# Research helper: prints the REST request/response shape from the official image generation guide.
set -u
curl -fsSL https://ai.google.dev/gemini-api/docs/image-generation.md.txt -o /tmp/guide.txt 2>/dev/null || { echo "(guide not reachable)"; exit 0; }
echo "## lines: $(wc -l < /tmp/guide.txt)"
echo "## REST-related lines"
grep -n -iE 'curl |generativelanguage|generateContent|interactions|x-goog-api-key|"contents"|"parts"|inline|"aspect|aspect_ratio|modalit|image_config|imageConfig|"model"|"input"|"data"|mime|base64|response_format|"output' /tmp/guide.txt | cut -c1-210 | awk '!seen[substr($0, index($0,":")+1)]++' | head -75
