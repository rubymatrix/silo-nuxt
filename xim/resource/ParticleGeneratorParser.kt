package xim.resource

import xim.math.Axis
import xim.resource.TextureCoordinateUpdater.Axis.X
import xim.resource.TextureCoordinateUpdater.Axis.Y
import xim.util.OnceLogger.warn
import xim.util.PI_f


class ParticleGeneratorParser(private val sectionHeader: SectionHeader) : ResourceParser {

    private val generatorDef = ParticleGeneratorDefinition(sectionHeader.sectionId)

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val effectResource = EffectResource(sectionHeader.sectionId, generatorDef)
        return ParserResult.from(effectResource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader)

        val attachFlags = byteReader.next16()
        val attachTypeFlag = attachFlags and 0x0F
        val attachType = AttachType.from(attachTypeFlag)
        if (attachType == null) { warn("Unknown attach type ${attachTypeFlag.toString(0x10)} in ${sectionHeader.sectionId}") }
        generatorDef.attachType = attachType ?: AttachType.None

        generatorDef.attachedJoint0 = (attachFlags and 0x03F0) ushr (0x4)
        generatorDef.attachedJoint1 = (attachFlags and 0xFC00) ushr (0xA)

        val additionalAttachFlags = byteReader.next16()
        generatorDef.attachSourceOriented = (additionalAttachFlags and 0x0001) != 0

        val actorPositionScaleTarget = when {
            (additionalAttachFlags and 0x0040) != 0 -> ActorScaleTarget.Source
            (additionalAttachFlags and 0x0080) != 0 -> ActorScaleTarget.Target
            else -> ActorScaleTarget.None
        }

        val actorSizeScaleTarget = when {
            (additionalAttachFlags and 0x0400) != 0 -> ActorScaleTarget.Source
            (additionalAttachFlags and 0x0800) != 0 -> ActorScaleTarget.Target
            else -> ActorScaleTarget.None
        }

        byteReader.offsetFromDataStart(sectionHeader, 0x10)
        val actorPositionScaleAmount = byteReader.nextFloat()
        val actorSizeScaleAmount = byteReader.nextFloat()

        generatorDef.actorScaleParams = ActorScaleParams(
            scalePosition = actorPositionScaleTarget,
            scaleSize = actorSizeScaleTarget,
            scalePositionAmount = actorPositionScaleAmount,
            scaleSizeAmount = actorSizeScaleAmount,
        )

        byteReader.offsetFromDataStart(sectionHeader, 0x50)
        generatorDef.unkId = byteReader.next32()
        generatorDef.environmentId = byteReader.nextDatId().toNullIfZero()

        byteReader.offsetFrom(sectionHeader, 0x74)

        generatorDef.emissionVariance = byteReader.next16().toFloat()
        generatorDef.framesPerEmission = byteReader.next16().toFloat() + 1
        generatorDef.particlesPerEmission = byteReader.next8()

        val genFlags = byteReader.next8()
        generatorDef.continuousSingleton = genFlags and 0x04 != 0
        generatorDef.autoRun = genFlags and 0x10 != 0

        val unk = byteReader.next8()

        val moreFlags = byteReader.next8()
        generatorDef.batched = (moreFlags and 0x20) != 0 // Groups of particles should be processed as a single unit; generally for weather

        byteReader.offsetFrom(sectionHeader, 0x80)
        val section1Offset = byteReader.next32()
        val section2Offset = byteReader.next32()
        val section3Offset = byteReader.next32()
        val section4Offset = byteReader.next32()

        byteReader.offsetFrom(sectionHeader, section1Offset)
        parseSection(byteReader, 1, generatorDef)

        byteReader.offsetFrom(sectionHeader, section2Offset)
        parseSection(byteReader, 2, generatorDef)

        byteReader.offsetFrom(sectionHeader, section3Offset)
        parseSection(byteReader, 3, generatorDef)

        byteReader.offsetFrom(sectionHeader, section4Offset)
        parseSection(byteReader, 4, generatorDef)
    }

    private fun parseSection(byteReader: ByteReader, secNum: Int, particleGenerator: ParticleGeneratorDefinition) {
        while (true) {
            val startPosition = byteReader.position

            val opCodeConfig = byteReader.next32()
            val opCode = opCodeConfig and 0xFF
            val opCodeSize = ((opCodeConfig shr 8) and 0x1F)
            val allocationOffset = opCodeConfig shr 0xD // This is a reference to the dynamically allocated data - in game, it's an offset into per-particle allocated memory

            if (opCode == 0x00) { return }

            val handled = when (secNum) {
                1 -> sec1Handler(byteReader, opCode, particleGenerator)
                2 -> sec2Handler(byteReader, opCode, allocationOffset, particleGenerator)
                3 -> sec3Handler(byteReader, opCode, opCodeSize, allocationOffset, particleGenerator)
                4 -> sec4Handler(byteReader, opCode, particleGenerator)
                else -> false
            }

            val nextCodePos = startPosition + opCodeSize * 4

            if (!handled) {
                val args = (0 until (opCodeSize - 1)).map { byteReader.next32().toString(0x10) }
                warn("[${sectionHeader.sectionId}] [$byteReader] Unknown Particle Sec$secNum OpCode: ${opCode.toString(0x10)} - args: $args")
            } else if (byteReader.position != nextCodePos) {
                throw IllegalStateException("Read wrong amount! [$secNum] [${opCode.toString(0x10)}] ${byteReader.position.toString(0x10)} vs ${nextCodePos.toString(0x10)}")
            }

            byteReader.position = nextCodePos
        }
    }

    private fun sec1Handler(byteReader: ByteReader, opCode: Int, definition: ParticleGeneratorDefinition): Boolean {
        val updater = when (opCode) {
            0x04 -> EmissionFrequencyUpdater()
            0x05 -> RelativeVelocityUpdater()
            0x06 -> SphericalPositionUpdater { p, v -> p.baseRadius = v }
            0x07 -> SphericalPositionUpdater { p, v -> p.radiusVariance = v }
            0x08 -> SphericalPositionUpdater { p, v -> p.rotationZAxis = v * PI_f }
            0x09 -> SphericalPositionUpdater { p, v -> p.rotationYAxis = v * PI_f }
            0x0A -> GeneratorCullUpdater()
            0x0B -> GeneratorBasePositionUpdater(axis = Axis.X)
            0x0C -> GeneratorBasePositionUpdater(axis = Axis.Y)
            0x0D -> GeneratorBasePositionUpdater(axis = Axis.Z)
            0x0E -> GeneratorRotationUpdater(axis = Axis.X)
            0x0F -> GeneratorRotationUpdater(axis = Axis.Y)
            0x10 -> GeneratorRotationUpdater(axis = Axis.Z)
            0x11 -> AssociationUpdater()
            0x12 -> GeneratorVelocityUpdater(axis = Axis.X)
            0x13 -> GeneratorVelocityUpdater(axis = Axis.Y)
            0x14 -> GeneratorVelocityUpdater(axis = Axis.Z)
            else -> {
                return false
            }
        }

        updater.read(byteReader, ReadContext(sectionHeader.sectionId, definition))
        definition.generatorUpdaters += updater

        return true
    }

    private fun sec2Handler(byteReader: ByteReader, opCode: Int, allocationOffset: Int, generator: ParticleGeneratorDefinition): Boolean {
        val initializer: ParticleInitializer = when (opCode) {
            0x01 -> StandardParticleSetup()

            0x02 -> TranslationVelocitySetup(allocationOffset)
            0x03 -> VelocityVarianceSetup(allocationOffset) // Position velocity variance

            0x06 -> SphericalPositionVarianceSimple()
            0x07 -> SphericalPositionVarianceMedium()

            0x08 -> RelativeVelocitySetup(allocationOffset)

            0x09 -> RotationInitializer()
            0x0A -> RotationVarianceInitializer()

            0x0B -> RotationVelocitySetup(allocationOffset)
            0x0C -> VelocityVarianceSetup(allocationOffset) // Rotation velocity variance

            0x0F -> ScaleInitializer()
            0x10 -> ScaleVarianceInitializer()
            0x11 -> SingleScaleVarianceInitializer()

            0x12 -> ScaleVelocitySetup(allocationOffset)
            0x13 -> VelocityVarianceSetup(allocationOffset) // Scale velocity variance

            0x16 -> ColorSetup()
            0x17 -> ColorVarianceSetup()
            0x18 -> UniformColorVarianceSetup()

            0x19 -> ColorTransformSetup(allocationOffset)
            0x1A -> ColorTransformVariance(allocationOffset)

            0x1D -> SpriteSheetInitializer()
            0x1E -> BlendFuncInitializer()
            0x1F -> SphericalPositionVarianceFull()

            0x21, // 0x0F: Position.x
            0x22, // 0x10: Position.y
            0x23, // 0x11: Position.z
            0x24, // 0x12: Rotation.x
            0x25, // 0x13: Rotation.y
            0x26, // 0x14: Rotation.z
            0x27, // 0x15: Scale.x
            0x28, // 0x16: Scale.y
            0x29, // 0x17: Scale.z
            0x2A, // 0x18: Color.r
            0x2B, // 0x19: Color.g
            0x2C, // 0x1A: Color.b
            0x2D, // 0x1B: Color.a
            0x2E, // 0x1C: TexCoord.u
            0x2F  // 0x1D: TexCoord.u
                -> KeyFrameValueSetup(allocationOffset)

            0x30 -> DepthBiasInitializer()
            0x31 -> RandomVelocitySetup(allocationOffset) // Only used for scale? Overwrites VelocitySetup/VelocityVarianceSetup
            0x32 -> HazeOffsetInitializer()

            0x33, // Weight Mesh[0]
            0x34, // Weight Mesh[1]
            0x35, // Weight Mesh[2]
            0x36, // Weight Mesh[3]
            0x37, // Weight Mesh[4]
                -> KeyFrameValueSetup(allocationOffset)

            0x39 -> KeyFrameValueSetup(allocationOffset)

            0x3A -> RingMeshSetup()

            0x3B -> IncrementalRotationApplier()

            0x3C -> OnceChildGeneratorSetup()

            0x3D -> OscillationSetup(allocationOffset)
            0x3E -> OscillationAccelerationSetup(allocationOffset, Axis.X)
            0x3F -> OscillationAccelerationSetup(allocationOffset, Axis.Y)
            0x40 -> OscillationAccelerationSetup(allocationOffset, Axis.Z)

            0x41 -> RelativeVelocityVarianceSetup(allocationOffset)
            0x42 -> GroundProjectionSetup()
            0x43 -> DeferredBlendFuncInitializer()
            0x44 -> ChildGeneratorSetup(allocationOffset)
            0x45 -> ParentPositionCopyConfig()
            0x46 -> ParentVelocityConfig(allocationOffset)
            0x47 -> ParentRotateConfig()
            0x48 -> ParentColorConfig()
            0x49 -> ParentScaleConfig()
            0x4A -> ParentTexCoordConfig()

            0x4C -> AudioRangeSetup()

            0x4E -> FixedPointPositionVarianceSetup()
            0x4F -> FixedPointPositionVarianceSetup()

            0x50, // Velocity.x
            0x51, // Velocity.y
            0x52, // Velocity.z
                -> KeyFrameValueSetup(allocationOffset)

            0x53 -> ChildGeneratorSetup(allocationOffset)
            0x54 -> PointListPositionSetup(allocationOffset)
            0x55 -> SpecularParamsInitializer()
            0x56 -> BatchingSetup()
            0x58 -> PointLightParamsInitializer()

            0x59, // Specular Rotation.x
            0x5A, // Specular Rotation.y
            0x5B, // Specular Rotation.z
            0x5C, // Specular Color.r
            0x5D, // Specular Color.g
            0x5E, // Specular Color.b
            0x5F  // Specular Color.a
                -> KeyFrameValueSetup(allocationOffset)

            0x60, // 0x3C: ToD Color.r
            0x61, // 0x3D: ToD Color.g
            0x62, // 0x3E: ToD Color.b
            0x63, // 0x3F: ToD Color.a
                -> KeyFrameValueSetup(allocationOffset)

            0x64, // 0x3D: ToD Scale.x
            0x65, // 0x3E: ToD Scale.y
            0x66, // 0x3F: ToD Scale.z
                -> KeyFrameValueSetup(allocationOffset)

            0x67 -> ReverseDisplacementSetup(allocationOffset)

            0x68 -> KeyFrameValueSetup(allocationOffset) // ToD Volume

            0x69 -> KeyFrameValueSetup(allocationOffset) // 0x44: Velocity Dampener

            0x6A -> ChildGeneratorSetup(allocationOffset)

            0x6B -> PathReferenceSetup()
            0x6C -> KeyFrameValueSetup(allocationOffset) // 0x49: Point Light Params

            0x6D -> KeyFrameValueSetup(allocationOffset) // 0x4A: ToD Specular Color
            0x6E -> KeyFrameValueSetup(allocationOffset) // 0x4B: ToD Specular Color
            0x6F -> KeyFrameValueSetup(allocationOffset) // 0x4C: ToD Specular Color
            0x70 -> KeyFrameValueSetup(allocationOffset) // 0x4D: ToD Specular Color

            0x72 -> ProjectionBiasInitializer()

            0x74 -> KeyFrameValueSetup(allocationOffset) // UV.x - velocity
            0x75 -> KeyFrameValueSetup(allocationOffset) // UV.y - velocity

            0x76 -> KeyFrameValueSetup(allocationOffset) // Rotational velocity x
            0x77 -> KeyFrameValueSetup(allocationOffset) // Rotational velocity y
            0x78 -> KeyFrameValueSetup(allocationOffset) // Rotational velocity z

            0x79 -> ParentRotateConfig() // How does it differ from 0x47?

            0x7B -> ProgressPositionOffsetConfig()

            0x7C -> KeyFrameValueSetup(allocationOffset) // 0x5B: Point light params - theta
            0x7D -> KeyFrameValueSetup(allocationOffset) // 0x5C: Point light params - range

            0x7E -> ParentThetaConfig()
            0x7F -> ParentRangeConfig()

            0x80 -> KeyFrameValueSetup(allocationOffset) // 0x5D: Point light params - theta-mult
            0x81 -> KeyFrameValueSetup(allocationOffset) // 0x5E: Point light params - range-mult

            0X82 -> CameraShakeSetup(allocationOffset)

            0x83 -> KeyFrameValueSetup(allocationOffset) // ToD rotation.x velocity
            0x84 -> KeyFrameValueSetup(allocationOffset) // ToD rotation.y velocity
            0x85 -> KeyFrameValueSetup(allocationOffset) // ToD rotation.z velocity

            0x88 -> PointLightAttachmentSetup()

            0x8B -> KeyFrameValueSetup(allocationOffset) // ToD rotation.x
            0x8C -> KeyFrameValueSetup(allocationOffset) // ToD rotation.y
            0x8D -> KeyFrameValueSetup(allocationOffset) // ToD rotation.z

            0x8E -> FootMarkEffectSetup()

            0x90 -> DaylightBasedColorAdjuster()
            0x91 -> DaylightBasedColorSetup(allocationOffset)

            0x95 -> KeyFrameValueSetup(allocationOffset) // ToD position.x
            0x96 -> KeyFrameValueSetup(allocationOffset) // ToD position.y
            0x97 -> KeyFrameValueSetup(allocationOffset) // ToD position.z

            0x9B -> ParentPositionSnapshotConfig()

            else -> {
                return false
            }
        }

        initializer.read(byteReader, ReadContext(sectionHeader.sectionId, generator))
        generator.initializers += initializer
        return true
    }

    private fun sec3Handler(byteReader: ByteReader, opCode: Int, opCodeSize: Int, allocationOffset: Int, generator: ParticleGeneratorDefinition): Boolean {
        val updater: ParticleUpdater = when (opCode) {
            0x02 -> PositionUpdater(allocationOffset)
            0x03 -> VelocityAccelerator(allocationOffset)
            0x05 -> RotationUpdater(allocationOffset)
            0x06 -> VelocityAccelerator(allocationOffset)
            0x08 -> ScaleUpdater(allocationOffset)
            0x09 -> VelocityAccelerator(allocationOffset)

            0x0B -> ColorTransformApplier(allocationOffset)
            0x0C -> ColorTransformModifier(allocationOffset)

            0x0D -> SpriteSheetFrameUpdater()
            0x0E -> NoOpParticleUpdater() // This seems to be related to advancing the particle's age

            0x0F -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.position.x = v })
            0x10 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.position.y = v })
            0x11 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.position.z = v })

            // The normalization is kinda weird, but it seems necessary for Haste/Slow/Shell
            0x12 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.rotation.x / PI_f }, updateFn = { p, v -> p.rotation.x = v * PI_f } )
            0x13 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.rotation.y / PI_f }, updateFn = { p, v -> p.rotation.y = v * PI_f } )
            0x14 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.rotation.z / PI_f }, updateFn = { p, v -> p.rotation.z = v * PI_f } )

            0x15 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.scale.x }, updateFn = { p, v -> p.scale.x = v })
            0x16 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.scale.y }, updateFn = { p, v -> p.scale.y = v })
            0x17 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.scale.z }, updateFn = { p, v -> p.scale.z = v })

            0x18 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.color.r() }, updateFn = { p, v -> p.color.r(v) })
            0x19 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.color.g() }, updateFn = { p, v -> p.color.g(v) })
            0x1A -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.color.b() }, updateFn = { p, v -> p.color.b(v) })
            0x1B -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.color.a() }, updateFn = { p, v -> p.color.a(v) })

            0x1C -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.texCoordTranslate.x = v })
            0x1D -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.texCoordTranslate.y = v })

            0x1E -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.weightedMeshWeights[0] = v })
            0x1F -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.weightedMeshWeights[1] = v })
            0x20 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.weightedMeshWeights[2] = v })
            0x21 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.weightedMeshWeights[3] = v })
            0x22 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.weightedMeshWeights[4] = v })

            0x24 -> ProgressValueUpdater(allocationOffset, initialValueFn = { p -> p.hazeOffset.x }, updateFn = { p, v -> p.hazeOffset.x = v})

            0x25 -> ChildGeneratorBasicUpdater(allocationOffset)
            0x26 -> VelocityRotator(allocationOffset)

            0x27 -> TextureCoordinateUpdater(axis = X)
            0x28 -> TextureCoordinateUpdater(axis = Y)

            0x29 -> OscillationApplier(allocationOffset, axis = Axis.X)
            0x2A -> OscillationApplier(allocationOffset, axis = Axis.Y)
            0x2B -> OscillationApplier(allocationOffset, axis = Axis.Z)

            0x2C -> VelocityDampener(allocationOffset)

            0x2E -> DrawDistanceUpdater()
            0x2F -> VelocityRotationUpdater(allocationOffset)

            // These effects directly modify velocity. If nothing reads velocity (ie 0x02), then it's effectively a no-op.
            // As such, this effect is a no-op in nearly all particles that try to use it...
            0x30 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.getDynamic(PositionTransform::class)?.velocity?.x = v })
            0x31 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.getDynamic(PositionTransform::class)?.velocity?.y = v })
            0x32 -> ProgressValueUpdater(allocationOffset, updateFn = { p, v -> p.getDynamic(PositionTransform::class)?.velocity?.z = v })

            0x33 -> ChildGeneratorUpdater(allocationOffset, billBoardType = BillBoardType.None)
            0x34 -> PointListPositionUpdater(allocationOffset)

            0x35 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.rotation.x }, updateFn = { p, v -> p.specularParams!!.rotation.x = v })
            0x36 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.rotation.y }, updateFn = { p, v -> p.specularParams!!.rotation.y = v })
            0x37 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.rotation.z }, updateFn = { p, v -> p.specularParams!!.rotation.z = v })

            0x38 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.color.r() }, updateFn = { p, v -> p.specularParams!!.color.r(v) })
            0x39 -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.color.g() }, updateFn = { p, v -> p.specularParams!!.color.g(v) })
            0x3A -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.color.b() }, updateFn = { p, v -> p.specularParams!!.color.b(v) })
            0x3B -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.specularParams!!.color.a() }, updateFn = { p, v -> p.specularParams!!.color.a(v) })

            // Verified that RGB are setters (overrides 0x16), and A is a multiplier (combines with 0x16)
            0x3C -> ClockValueUpdater(allocationOffset) { p, v -> p.color.r(v) }
            0x3D -> ClockValueUpdater(allocationOffset) { p, v -> p.color.g(v) }
            0x3E -> ClockValueUpdater(allocationOffset) { p, v -> p.color.b(v) }
            0x3F -> ClockValueUpdater(allocationOffset) { p, v -> p.colorMultiplier.multiplyAlphaInPlace(v) }

            0x40 -> ClockValueUpdater(allocationOffset) { p, v -> p.scale.x = v }
            0x41 -> ClockValueUpdater(allocationOffset) { p, v -> p.scale.y = v }
            0x42 -> ClockValueUpdater(allocationOffset) { p, v -> p.scale.z = v }

            0x43 -> ClockValueUpdater(allocationOffset) { p, v -> p.audioConfiguration.volumeMultiplier = v }

            0x44 -> ProgressValueUpdater(allocationOffset) { p, v -> p.getPositionTransform().dampeningFactor = v }

            0x45 -> MoonPhaseSpriteSheetUpdater()
            0x46 -> ChildGeneratorUpdater(allocationOffset, billBoardType = BillBoardType.XYZ)
            0x48 -> DoubleRangeDrawDistanceUpdater()
            0x49 -> ClockValueUpdater(allocationOffset) { p, v -> p.pointLightParams.theta = v }

            0x4A -> ClockValueUpdater(allocationOffset) { p, v -> p.specularParams!!.color.r(v) }
            0x4B -> ClockValueUpdater(allocationOffset) { p, v -> p.specularParams!!.color.g(v) }
            0x4C -> ClockValueUpdater(allocationOffset) { p, v -> p.specularParams!!.color.b(v) }
            0x4D -> ClockValueUpdater(allocationOffset) { p, v -> p.specularParams!!.color.a(v) }

            0x4E -> DayOfWeekColorUpdater()
            0x4F -> MoonPhaseColorUpdater()

            0x53 -> OcclusionUpdater()

            0x54 -> ProgressValueUpdater(allocationOffset, integrate = true, updateFn = { p, v -> p.texCoordTranslate.x += v })
            0x55 -> ProgressValueUpdater(allocationOffset, integrate = true, updateFn = { p, v -> p.texCoordTranslate.y += v })

            0x56 -> ProgressValueUpdater(allocationOffset, integrate = true, updateFn = { p, v -> p.rotation.x += v * PI_f } )
            0x57 -> ProgressValueUpdater(allocationOffset, integrate = true, updateFn = { p, v -> p.rotation.y += v * PI_f } )
            0x58 -> ProgressValueUpdater(allocationOffset, integrate = true, updateFn = { p, v -> p.rotation.z += v * PI_f } )

            0x59 -> AngularDistanceRotationUpdater()

            0x5B -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.pointLightParams.theta }, updateFn = { p, v -> p.pointLightParams.theta = v })
            0x5C -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.pointLightParams.range }, updateFn = { p, v -> p.pointLightParams.range = v })

            // Confirmed that these two aren't 2^x (unlike the initializer)
            0x5D -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.pointLightParams.thetaMultiplier }, updateFn = { p, v -> p.pointLightParams.thetaMultiplier = v })
            0x5E -> ProgressValueUpdater(allocationOffset, initialValueFn = { it.pointLightParams.rangeMultiplier }, updateFn = { p, v -> p.pointLightParams.rangeMultiplier = v })

            0x5F -> CameraShakeUpdater(allocationOffset, opCodeSize)
            0x60 -> ScreenFlashApplier()

            0x61 -> ClockValueRotationUpdater(allocationOffset) { p, v -> p.rotation.x += v * PI_f }
            0x62 -> ClockValueRotationUpdater(allocationOffset) { p, v -> p.rotation.y += v * PI_f }
            0x63 -> ClockValueRotationUpdater(allocationOffset) { p, v -> p.rotation.z += v * PI_f }

            0x66 -> ClockValueUpdater(allocationOffset) { p, v -> p.rotation.z = v * PI_f }
            0x67 -> ClockValueUpdater(allocationOffset) { p, v -> p.rotation.z = v * PI_f }
            0x68 -> ClockValueUpdater(allocationOffset) { p, v -> p.rotation.z = v * PI_f }

            0x69 -> DaylightBasedColorApplier(allocationOffset)

            0x6B -> ClockValueUpdater(allocationOffset) { p, v -> p.position.x = v }
            0x6C -> ClockValueUpdater(allocationOffset) { p, v -> p.position.y = v }
            0x6D -> ClockValueUpdater(allocationOffset) { p, v -> p.position.z = v }

            0x6E -> DoubleRangeWeightedMeshUpdater()

            else -> {
                return false
            }
        }

        updater.read(byteReader, ReadContext(sectionHeader.sectionId, generator))
        generator.updaters += updater
        return true
    }

    private fun sec4Handler(byteReader: ByteReader, opCode: Int, generator: ParticleGeneratorDefinition): Boolean {
        val handler = when(opCode) {
            0x01 -> EmitChildHandler()
            0x05 -> RepeatExpirationHandler()
            else -> {
                return false
            }
        }

        handler.read(byteReader, ReadContext(sectionHeader.sectionId, generator))
        generator.expirationHandlers += handler
        return true
    }

}
