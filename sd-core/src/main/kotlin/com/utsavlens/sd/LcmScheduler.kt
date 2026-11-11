package com.utsavlens.sd

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Latent Consistency Model scheduler (epsilon prediction, scaled-linear betas), following the
 * published LCM sampling procedure. LCM needs only a few UNet passes and no classifier-free
 * guidance pass, which is what makes on-phone CPU generation tolerable.
 */
class LcmScheduler(
    val inferenceSteps: Int,
    strength: Float = 1f,
    private val trainSteps: Int = 1000,
    private val originalSteps: Int = 50,
    betaStart: Double = 0.00085,
    betaEnd: Double = 0.012,
    private val timestepScaling: Double = 10.0,
    private val sigmaData: Double = 0.5,
) {
    val alphasCumprod: DoubleArray
    /** Descending timesteps actually run, e.g. [999, 759, 499, 259] for 4 steps. */
    val timesteps: IntArray

    init {
        require(inferenceSteps in 1..originalSteps) { "steps must be 1..$originalSteps" }
        val s = sqrt(betaStart)
        val e = sqrt(betaEnd)
        var prod = 1.0
        alphasCumprod = DoubleArray(trainSteps) { i ->
            val b = s + (e - s) * i / (trainSteps - 1)
            prod *= 1.0 - b * b
            prod
        }
        val k = trainSteps / originalSteps
        val count = (originalSteps * strength.coerceIn(0.05f, 1f)).toInt().coerceAtLeast(inferenceSteps)
        val origin = IntArray(count) { (it + 1) * k - 1 }.reversedArray()
        timesteps = IntArray(inferenceSteps) { i -> origin[floor(i.toDouble() * count / inferenceSteps).toInt()] }
    }

    fun alpha(timestep: Int): Double = alphasCumprod[timestep.coerceIn(0, trainSteps - 1)]

    /** Forward diffusion: x_t = sqrt(a_t) x_0 + sqrt(1 - a_t) noise. */
    fun addNoise(clean: FloatArray, noise: FloatArray, timestep: Int): FloatArray {
        val a = alpha(timestep)
        val sa = sqrt(a).toFloat()
        val sn = sqrt(1.0 - a).toFloat()
        return FloatArray(clean.size) { i -> sa * clean[i] + sn * noise[i] }
    }

    class StepResult(val prevSample: FloatArray, val denoised: FloatArray)

    /**
     * One LCM step. [noise] is fresh gaussian noise used to re-noise to the next timestep; it is
     * ignored on the last step, where the denoised estimate is the result.
     */
    fun step(stepIndex: Int, sample: FloatArray, modelOutput: FloatArray, noise: FloatArray?): StepResult {
        val t = timesteps[stepIndex]
        val a = alpha(t)
        val sqrtA = sqrt(a)
        val sqrtB = sqrt(1.0 - a)
        val scaled = t * timestepScaling
        val cSkip = sigmaData * sigmaData / (scaled * scaled + sigmaData * sigmaData)
        val cOut = scaled / sqrt(scaled * scaled + sigmaData * sigmaData)
        val denoised = FloatArray(sample.size) { i ->
            val x0 = (sample[i] - sqrtB * modelOutput[i]) / sqrtA
            (cOut * x0 + cSkip * sample[i]).toFloat()
        }
        val last = stepIndex == inferenceSteps - 1
        if (last || noise == null) return StepResult(denoised, denoised)
        val aPrev = alpha(timesteps[stepIndex + 1])
        val sa = sqrt(aPrev).toFloat()
        val sn = sqrt(1.0 - aPrev).toFloat()
        return StepResult(FloatArray(sample.size) { i -> sa * denoised[i] + sn * noise[i] }, denoised)
    }

    companion object {
        /** Sinusoidal embedding of the guidance scale that LCM UNets take as `timestep_cond`. */
        fun guidanceEmbedding(guidanceScale: Float, dim: Int): FloatArray {
            val w = (guidanceScale - 1f) * 1000.0
            val half = dim / 2
            val factor = ln(10000.0) / (half - 1).coerceAtLeast(1)
            val out = FloatArray(dim)
            for (i in 0 until half) {
                val v = w * exp(-factor * i)
                out[i] = sin(v).toFloat()
                out[half + i] = cos(v).toFloat()
            }
            return out
        }
    }
}
