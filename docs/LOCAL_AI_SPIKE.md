# Local AI feasibility spike (Phase 8)

Date: 2026-11-11. Question: can on-device Stable Diffusion inpainting be built reliably in GitHub
Actions with no secrets, and is it usable on a phone?

## Options examined

### A. ONNX Runtime Android + SD 1.5-family ONNX weights downloaded at runtime
- Build: `com.microsoft.onnxruntime:onnxruntime-android:1.31.1` from Maven Central (version verified
  in Maven metadata). Pure Gradle dependency, no NDK, no secrets. **Builds in CI.**
- Weights: verified through the hosting API in CI (`tools/probe`, workflow "Probe model hosting"):
  `SimianLuo/LCM_Dreamshaper_v7` (model card licence: MIT; base model lineage SD 1.5,
  CreativeML OpenRAIL-M) ships a complete ONNX export with LFS SHA-256 checksums:
  text encoder 493 MB, UNet 3.44 GB (fp32, external data), VAE encoder 137 MB, VAE decoder 198 MB,
  tokenizer 1.6 MB. Total about 4.27 GB. Not gated, direct HTTPS download.
- Why an LCM model instead of the classic 9-channel "SD 1.5 inpainting" UNet:
  1. no ONNX export of the 9-channel inpainting UNet with verifiable checksums was found on a
     stable public host (the original runwayml repository is gone);
  2. classic SD needs 20-50 steps x 2 UNet passes (classifier-free guidance). LCM needs 4 steps x 1
     pass, roughly 10-25x less compute, which is the difference between "minutes" and "tens of
     minutes" on a phone CPU.
  Inpainting is done by latent blending (the protected latent cells are reset to the original
  image at every step), and the original person pixels are pasted back in pixel space afterwards,
  so the face is identical by construction, exactly as required.
- Runtime cost on a phone (estimate, NOT measured on a device): UNet fp32 maps about 3.4 GB of RAM,
  so realistically only 12 GB+ phones; expect roughly one to several minutes per image on a
  current flagship CPU. The device gate refuses phones with < 10 GiB RAM, < 8 cores, non-64-bit
  ABIs, Android < 9, or not enough free storage, with a reason instead of an OOM kill.
- NPU/GPU: not used. ONNX Runtime's NNAPI path is deprecated and QNN needs Qualcomm's SDK (see B).

### B. Integrating an existing open-source Android SD engine with Snapdragon NPU (Local Dream)
- Licence (read from the repository's LICENSE file): **CC BY-NC 4.0**, i.e. non-commercial only,
  and a Creative Commons licence, which is unusual and awkward for code reuse.
- Build requirements: C++ via NDK/CMake plus the **Qualcomm QNN SDK** (proprietary, licence
  click-through, not redistributable) and MNN; models must be pre-converted per chipset.
- CI: cannot be built in GitHub Actions without either committing/redistributing the QNN SDK or
  storing credentials as secrets. Both are ruled out by the project constraints.
- Verdict: **not viable** under "builds in Actions without secrets"; licence is a second blocker.

## Decision
Option A. (The user asked for all phases to proceed without stopping; this is the option the spike
recommends, and the only one that satisfies the CI constraint.)

## What was verified, and how
| Claim | Evidence |
|---|---|
| Builds in CI | `build.yml` assembles the APK with `onnxruntime-android` |
| Model files exist with these sizes/checksums | hosting API listing in the probe workflow log; re-hashed after download in `local-ai-validate.yml` |
| Pipeline code is correct with the real weights | `local-ai-validate.yml` runs `sd-core`'s `RealModelTest` on the runner CPU: tokenizer -> text encoder -> VAE encode -> 4 LCM UNet steps with latent blending -> VAE decode; asserts the protected area stays close to the original and the rest is really regenerated; uploads the PNG |
| Scheduler / tokenizer / mask maths | unit tests in `sd-core` (run in every build and locally with `-PjvmOnly`) |
| Download is resumable, checksum-verified, Wi-Fi-only capable, deletable | `ModelDownloader`, `ModelStore`, settings UI; store/gate logic unit-tested |

## What is NOT verified
- Speed, memory behaviour and thermal behaviour on real phones. Nothing in this project has been
  run on a physical device. Treat LOCAL AI as experimental exactly as the product brief says.
- Image quality of 512-px LCM output after upscaling to the photo's resolution (the generated
  background is upscaled with bilinear filtering; there is no neural upscaler).

## Known limits
- 4.3 GB download, about 3.5 GB RAM while generating.
- CPU only. No NPU/GPU acceleration.
- "Keep original background" on this engine means a light restyle (low strength), not zero change;
  use LITE for a strict overlays-only result.
