#!/usr/bin/env bash
# Research helper: prints facts from official sources that the build pins (model IDs, versions).
set -u
echo "## Gemini image / flash model IDs on the official deprecations page"
curl -fsSL https://ai.google.dev/gemini-api/docs/deprecations.md.txt 2>/dev/null \
  | grep -E 'gemini-[a-z0-9.-]*(image|flash|banana)' | grep -viE 'tts|live|audio|embedding|native' | cut -c1-260 | head -45 || echo "(deprecations page not reachable)"
echo "## Model IDs named on the image generation guide"
curl -fsSL https://ai.google.dev/gemini-api/docs/image-generation.md.txt 2>/dev/null \
  | grep -oE 'gemini-[a-z0-9.-]*(image|banana)[a-z0-9.-]*|imageConfig|aspectRatio|responseModalities|inline_data|inlineData' | sort | uniq -c | head -20 || echo "(guide not reachable)"
echo "## Google Maven latest versions"
for a in com/google/mediapipe/tasks-vision androidx/media3/media3-transformer; do
  echo "$a: $(curl -fsS https://dl.google.com/dl/android/maven2/$a/maven-metadata.xml | grep -oE '<version>[^<]+' | sed 's/<version>//' | tail -5 | tr '\n' ' ')"
done
