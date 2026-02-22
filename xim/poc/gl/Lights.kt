package xim.poc.gl

import xim.math.Vector3f

data class PointLight(val position: Vector3f, val color: Color, val range: Float, val attenuationQuad: Float)

data class DiffuseLight(val direction: Vector3f, val color: Color) {
    companion object {
        fun interpolate(d0: DiffuseLight, d1: DiffuseLight, t: Float): DiffuseLight {
            val direction = Vector3f.lerp(d0.direction, d1.direction, t)
            val color = Color.interpolate(d0.color, d1.color, t)
            return DiffuseLight(direction, color)
        }
    }
}