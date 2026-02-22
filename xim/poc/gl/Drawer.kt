package xim.poc.gl;

import xim.poc.camera.Camera

interface Drawer {

    fun drawXim(cmd: DrawXimCommand)
    fun setupXim(camera: Camera)

    fun drawXimSkinned(cmd: DrawXimCommand)
    fun setupXimSkinned(camera: Camera)

    fun drawXimParticle(cmd: DrawXimParticleCommand)
    fun setupXimParticle(viewCamera: Camera, projectionCamera: Camera = viewCamera)

    fun drawXimUi(cmd: DrawXimUiCommand)
    fun setupXimUi()

    fun drawXimDecal(cmd: DrawXimCommand)
    fun setupXimDecal(worldCamera: Camera)

    fun drawXimLensFlare(cmd: DrawXimCommand)
    fun setupXimLensFlare()

    fun drawScreenBuffer(sourceBuffer: GLScreenBuffer, destBuffer: GLScreenBuffer? = null, options: DrawXimScreenOptions = DrawXimScreenOptions())

    fun finish()

}
