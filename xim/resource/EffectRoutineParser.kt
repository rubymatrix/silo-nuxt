package xim.resource

import xim.math.Axis
import xim.poc.game.EffectDisplayer
import xim.poc.game.configuration.constants.SpellSkillId
import xim.util.OnceLogger.warn

fun interface OnCompleteEffect {
    fun onComplete(effectSequence: EffectRoutineInstance.EffectSequence)
}

class EffectRoutineDefinition {
    val effects: ArrayList<Effect> = ArrayList()
    val completionEffects: ArrayList<OnCompleteEffect> = ArrayList()
    var totalDelay = 0

    // Some routines are run on-demand from the server, but some seem to run automatically. Not sure what drives this.
    var autoRunHeuristic = false
}

class EffectRoutineSection(val sectionHeader: SectionHeader) : ResourceParser {

    private val effectRoutineDefinition = EffectRoutineDefinition()
    
    private var randomChildBlock: RandomChildRoutine? = null

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val resource = EffectRoutineResource(sectionHeader.sectionId, effectRoutineDefinition)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        // 0x0
        expectZero32(byteReader)
        expectZero32(byteReader)
        expectZero32(byteReader)
        expectZero32(byteReader)

        // 0x10
        val sec1Offset = byteReader.next32() + sectionHeader.sectionStartPosition
        val sec2Offset = byteReader.next32() + sectionHeader.sectionStartPosition
        val sec3Offset = byteReader.next32() + sectionHeader.sectionStartPosition
        effectRoutineDefinition.totalDelay = byteReader.next32()

        // Sec1
        byteReader.position = sec1Offset
        parseSection(byteReader, 1, effectRoutineDefinition, this::parseSection1)

        byteReader.position = sec2Offset
        parseSection(byteReader, 2, effectRoutineDefinition, this::parseSection2)

        byteReader.position = sec3Offset
        parseSection(byteReader, 3, effectRoutineDefinition, this::parseSection3)
    }

    fun parseSection(byteReader: ByteReader, secNum: Int, effectRoutineDefinition: EffectRoutineDefinition, opCodeHandler: (ByteReader, Int, Int, EffectRoutineDefinition) -> Boolean) {
        while (true) {
            val opCode = byteReader.next8()
            val unkCombo = byteReader.next16()

            val numInputs = (unkCombo and 0x1F) - 1 // includes the dword with the opcode
            val unk0 = byteReader.next8()

            val nextCodePos = byteReader.position + numInputs * 4

            val handled = opCodeHandler(byteReader, opCode, numInputs, effectRoutineDefinition)
            if (!handled) {
                val argsToParse = if (secNum == 2) { numInputs - 1 } else { numInputs }
                val args = (0 until argsToParse).map { byteReader.next32().toString(0x10) }
                warn("[${sectionHeader.sectionId}] [$byteReader] Unknown EffectRoutine Sec$secNum OpCode: ${opCode.toString(0x10)} - args: $argsToParse, $args")
            } else if (byteReader.position != nextCodePos) {
                throw IllegalStateException("Read wrong amount! [${opCode.toString(0x10)}] ${byteReader.position.toString(0x10)} vs ${nextCodePos.toString(0x10)}")
            }

            byteReader.position = nextCodePos
            if (opCode == 0x00) { return }
        }
    }

    private fun parseSection1(byteReader: ByteReader, opCode: Int, numArgs: Int, effectRoutineDefinition: EffectRoutineDefinition): Boolean {
        when (opCode) {
            0x00 -> return true
            0x69, 0x6A -> return true // Probably used to set up some state for conditionals?
        }

        return false
    }

    private fun parseSection2(byteReader: ByteReader, opCode: Int, numArgs: Int, effectRoutineDefinition: EffectRoutineDefinition) : Boolean {
        val delay = byteReader.next16()
        val duration = byteReader.next16()

        when (opCode) {
            0x00 -> {
                addEffectRoutine(EndRoutineMarker(delay = delay, duration = duration))
            }
            0x01 -> {
                addEffectRoutine(StartRoutineMarker(delay = delay, duration = duration))
            }
            0x02 -> {
                val ref = byteReader.nextDatId()
                expectZero32(byteReader)

                addEffectRoutine(ParticleGeneratorRoutine(delay = delay, duration = duration, ref))
            }
            0x03 -> {
                val ref = byteReader.nextDatId()
                expectZero32(byteReader)

                addEffectRoutine(LinkedEffectRoutine(delay = delay, duration = duration, ref))
            }
            0x05 -> {
                val ref = byteReader.nextDatId()
                expectZero32(byteReader)

                byteReader.nextFloat() // generally 1.0f
                byteReader.nextFloat() // generally 1.0f

                val transitionInTime = byteReader.next16()
                byteReader.next16() // always 0?
                val transitionOutTime = byteReader.next16()
                val maxLoop = byteReader.next16()

                val unk0 = byteReader.next32()
                val unk1 = byteReader.next32()

                addEffectRoutine(SkeletonAnimationRoutine(delay = delay, duration = duration, ref, transitionInTime = transitionInTime, transitionOutTime = transitionOutTime, maxLoops = maxLoop))
            }
            0x07 -> {
                expectZero32(byteReader)
                addEffectRoutine(AnimationLockEffect(delay = delay, duration = duration))
            }
            0x09 -> {
                val ref = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(LinkedEffectRoutine(delay = delay, duration = duration, ref, useTarget = true))
            }
            0x0A -> {
                val routine = if (numArgs == 7) {
                    parseSoundEffectEmitter(byteReader, delay = delay, duration = duration, target = SoundEffectTarget.Source)
                } else {
                    val ref = byteReader.nextDatId()
                    expectZero32(byteReader)
                    LinkedEffectRoutine(delay = delay, duration = duration, ref)
                }

                addEffectRoutine(routine)
            }
            0x0B -> {
                addEffectRoutine(parseSoundEffectEmitter(byteReader, delay = delay, duration = duration, target = SoundEffectTarget.Target))
            }
            0x0C -> {
                val rotation = byteReader.nextVector3f()
                val index = byteReader.next32()
                addEffectRoutine(ModelTranslationRoutine(delay = delay, duration = duration, rotation, index))
            }
            0x0D -> {
                val rotation = byteReader.nextVector3f()
                val index = byteReader.next32()
                addEffectRoutine(ModelRotationRoutine(delay = delay, duration = duration, rotation, index))
            }
            0x15 -> {
                addEffectRoutine(ActorPositionSnapshotEffect(delay = delay))
            }
            0x17 -> {
                addEffectRoutine(JointSnapshotEffect(delay = delay, snapshot = false))
            }
            0x19 -> {
                val spellIndex = byteReader.next32()
                EffectDisplayer.preloadSkillResource(SpellSkillId(spellIndex))
                addEffectRoutine(SpellEffect(delay = delay, spellIndex = spellIndex))
            }
            0x1E -> {
                val particleGenRef = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(ParticleDampenRoutine(delay = delay, duration = duration, particleGenRef))
            }
            0x21 -> {
                addEffectRoutine(parseFlinchEffect(byteReader = byteReader, delay = delay, useTarget = false))
            }
            0x22 -> {
                byteReader.next32()
                addEffectRoutine(JointSnapshotEffect(delay = delay, snapshot = true))
            }
            0x25 -> {
                addEffectRoutine(parseFlinchEffect(byteReader = byteReader, delay = delay, useTarget = true))
            }
            0x27 -> {
                val resourceId = byteReader.nextDatId()
                expectZero32(byteReader)

                val flags0 = byteReader.next32() // 1, 3, 9, b
                val flags1 = byteReader.next32() // 0, 1, 2, 4
                val rotation = byteReader.nextFloat()

                byteReader.next32() // Always 0 except for ROM2/19/127.DAT
                expectZero32(byteReader)
                expectZero32(byteReader)

                addEffectRoutine(FollowPointsRoutine(delay = delay, duration = duration, resourceId = DatLink(resourceId), rotation = rotation, flags0 = flags0, flags1 = flags1))
            }
            0x28 -> {
                val transitionTime = byteReader.nextFloat()
                addEffectRoutine(TransitionToIdleEffect(delay = delay, transitionTime = transitionTime))
            }
            0x29 -> {
                val color = byteReader.nextRGBA()
                expectZero32(byteReader)
                addEffectRoutine(ActorFadeRoutine(delay = delay, duration = duration, endColor = color, useTarget = false))
            }
            0x2A -> {
                val color = byteReader.nextRGBA()
                expectZero32(byteReader)
                addEffectRoutine(ActorFadeRoutine(delay = delay, duration = duration, endColor = color, useTarget = true))
            }
            0x2B -> {
                expectZero32(byteReader)
                addEffectRoutine(DamageCallbackRoutine(delay = delay))
            }
            0x2C -> {
                val resourceId = byteReader.nextDatId()
                byteReader.next32()
                val color1 = byteReader.nextRGBA()
                val color2 = byteReader.nextRGBA()

                byteReader.nextFloat(4)

                byteReader.nextFloat(4)

                val squeeze1 = byteReader.nextFloat()
                val squeeze2 = byteReader.nextFloat()
                byteReader.nextFloat()
                byteReader.next16()
                byteReader.next16()

                byteReader.nextFloat(3)
                byteReader.next16()
                byteReader.next16()

                addEffectRoutine(WeaponTraceRoutine(
                    delay = delay,
                    duration = duration,
                    resourceId = DatLink(resourceId),
                    color1 = color1,
                    color2 = color2,
                    squeeze1 = squeeze1,
                    squeeze2 = squeeze2,
                ))
            }
            0x2D -> {
                val id = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(StopParticleGeneratorRoutine(delay = delay, id = id))
            }
            0x2E -> {
                addEffectRoutine(MovementLockEffect(delay = delay, duration = duration))
            }
            0x2F -> {
                addEffectRoutine(FacingLockEffect(delay = delay, duration = duration))
            }
            0x31 -> {
                addEffectRoutine(ToggleBroadcastEffect(delay = delay, duration = duration, useBroadcast = true))
            }
            0x32 -> {
                addEffectRoutine(ToggleBroadcastEffect(delay = delay, duration = duration, useBroadcast = false))
            }
            0x3B, 0x3C -> {
                val ref = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(LinkedEffectRoutine(delay = delay, duration = duration, ref, blocking = true))
            }
            0x3D -> {
                val randomChildRoutine = RandomChildRoutine(delay = delay, duration = duration)
                addEffectRoutine(randomChildRoutine)

                if (randomChildBlock != null) { throw IllegalStateException("Nested randoms?") }
                randomChildBlock = randomChildRoutine
            }
            0x3E -> {
                if (randomChildBlock == null) { throw IllegalStateException("But the block was never opened?") }
                randomChildBlock = null
            }
            0x3F -> {
                val effectId0 = byteReader.nextDatId()
                expectZero32(byteReader)

                val effectId1 = byteReader.nextDatId()
                expectZero32(byteReader)

                expectZero32(byteReader)

                addEffectRoutine(TransitionParticleEffect(delay = delay, duration = duration, stopEffect = effectId0, startEffect = effectId1))
            }
            0x40 -> {
                val textureId = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapTexture(delay = delay, duration = duration, textureLink = DatLink(textureId), flags = 0, useTarget = false))
            }
            0x41 -> {
                val textureId = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapTexture(delay = delay, duration = duration, textureLink = DatLink(textureId), flags = 0, useTarget = true))
            }
            0x42 -> {
                val endValue = byteReader.nextFloat()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapUvTranslation(delay = delay, duration = duration, endValue = endValue, uv = Axis.X, useTarget = false))
            }
            0x43 -> {
                val endValue = byteReader.nextFloat()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapUvTranslation(delay = delay, duration = duration, endValue = endValue, uv = Axis.Y, useTarget = false))
            }
            0x44 -> {
                val endValue = byteReader.nextFloat()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapUvTranslation(delay = delay, duration = duration, endValue = endValue, uv = Axis.X, useTarget = true))
            }
            0x45 -> {
                val endValue = byteReader.nextFloat()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapUvTranslation(delay = delay, duration = duration, endValue = endValue, uv = Axis.Y, useTarget = true))
            }
            0x46 -> {
                val endValue = byteReader.nextBGRA()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapColor(delay = delay, duration = duration, endValue = endValue, useTarget = false))
            }
            0x47 -> {
                val endValue = byteReader.nextBGRA()
                expectZero32(byteReader)
                addEffectRoutine(ActorWrapColor(delay = delay, duration = duration, endValue = endValue, useTarget = true))
            }
            0x4A -> {
                addEffectRoutine(parseSoundEffectEmitter(byteReader, delay = delay, duration = duration, target = SoundEffectTarget.PlayerOnly))
            }
            0x52 -> {
                effectRoutineDefinition.autoRunHeuristic = true

                val startTime = byteReader.next32()
                val endTime = byteReader.next32()
                val intervalDuration = byteReader.next32()
                val unk = byteReader.next32()

                addEffectRoutine(TimeBasedReplayRoutine(
                    delay = delay,
                    duration = duration,
                    timeOfDayStart = startTime,
                    timeOfDayEnd = endTime,
                    loopInterval = intervalDuration,
                ))
            }
            0x53 -> {
                addEffectRoutine(parseSoundEffectEmitter(byteReader, delay = delay, duration = duration, target = SoundEffectTarget.NearestTarget))
            }
            0x54 -> {
                val textureId = byteReader.nextDatId()
                expectZero32(byteReader)
                val unkFlags = byteReader.next32()
                addEffectRoutine(ActorWrapTexture(delay = delay, duration = duration, textureLink = DatLink(textureId), flags = unkFlags, useTarget = false))
            }
            0x55 -> {
                val textureId = byteReader.nextDatId()
                expectZero32(byteReader)
                val unkFlags = byteReader.next32()
                addEffectRoutine(ActorWrapTexture(delay = delay, duration = duration, textureLink = DatLink(textureId), flags = unkFlags, useTarget = true))
            }
            0x57 -> {
                val routineId = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(LinkedEffectRoutine(delay = delay, duration = duration, routineId))
            }
            0x59 -> {
                addEffectRoutine(AnimationLockEffect(delay = delay, duration = duration))
            }
            0x5A -> {
                val unk0 = byteReader.nextFloat() // Always 1.0
                val unk1a = byteReader.next16() // Always 4
                val blockType = byteReader.next16()
                val unk2 = byteReader.nextFloat() // Always 1.0
                val animationDuration = byteReader.nextFloat() // Always 16.0
                val unk4 = byteReader.next32() // Always 0.0
                addEffectRoutine(AttackBlockedRoutine(delay = delay, blockType = blockType, animationDuration = animationDuration))
            }
            0x5C -> {
                expectZero32(byteReader)
                addEffectRoutine(AttackCounteredRoutine(delay = delay))
            }
            0x5E, 0xBF -> {
                val unk0a = byteReader.next16()
                val unk0b = byteReader.next16()
                val animationDuration = byteReader.nextFloat()
                val unk2 = byteReader.nextFloat()
                val unk3 = byteReader.next32()
                addEffectRoutine(KnockBackRoutine(delay = delay, animationDuration = animationDuration))
            }
            0x5F -> {
                val routineId = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(StopRoutineEffect(delay = delay, id = routineId))
            }
            0x60 -> {
                addEffectRoutine(parseSoundEffectEmitter(byteReader, delay = delay, duration = duration, SoundEffectTarget.Global))
            }
            0x64 -> {
                addEffectRoutine(ControlFlowBranch(delay = delay, duration = duration, branchType = true))
            }
            0x67 -> {
                addEffectRoutine(ControlFlowBranch(delay = delay, duration = duration, branchType = false))
            }
            0x69 -> {
                addEffectRoutine(ControlFlowBlock(delay = 0, duration = duration, openBlock = true))
            }
            0x6A -> {
                addEffectRoutine(ControlFlowBlock(delay = 0, duration = duration, openBlock = false))
            }
            0x6B -> {
                // Condition - 2 or 3 params
                // Always comes in sets of 3, aside from one weird case in [crtl]

                val arg0 = byteReader.next16() // First two are always 0x1C, third is 0xC, 0xE, 0xF, or 0x11
                val arg1 = byteReader.next16() // First is always 0x3, second is always 0x1, third is always 0x0
                val input = if (numArgs == 3) { byteReader.next32() } else { null } // Always null for third; varies for first & second - null in a few cases
                if (arg0 == 0x1C && input == null) { warn("[${sectionHeader.sectionId}] Null input for R0/R1?") }
                addEffectRoutine(ControlFlowCondition(delay = delay, duration = duration, arg0 = arg0, arg1 = arg1, input = input))
            }
            0x6E -> {
                val displacement = byteReader.nextFloat()
                addEffectRoutine(ForwardDisplacementEffect(delay = delay, duration = duration, displacement = displacement))
            }
            0x6F -> {
                val particleGenId = byteReader.nextDatId()
                expectZero32(byteReader)
                val endValue = byteReader.nextFloat()
                addEffectRoutine(PointLightInterpolationEffect(delay = delay, duration = duration, particleGenId = particleGenId, endValue = endValue, theta = true))
            }
            0x70 -> {
                val particleGenId = byteReader.nextDatId()
                expectZero32(byteReader)
                val endValue = byteReader.nextFloat()
                addEffectRoutine(PointLightInterpolationEffect(delay = delay, duration = duration, particleGenId = particleGenId, endValue = endValue, theta = false))
            }
            0x73 -> {
                val loopId = byteReader.nextDatId()
                expectZero32(byteReader)
                expectZero32(byteReader)    // Always 0 - I wonder if it's something like loop-delay?
                addEffectRoutine(StartLoopRoutine(delay = delay, duration = duration, refId = loopId))
            }
            0x75 -> {
                val hidden = byteReader.next32() == 1
                val slot = byteReader.next16()
                val ifEngaged = byteReader.next16() == 1
                addEffectRoutine(SetModelVisibilityRoutine(delay = delay, hidden = hidden, slot = slot, ifEngaged = ifEngaged))
            }
            0x76 -> {
                val unk = byteReader.next32() // 0, 1, 2...
                addEffectRoutine(StartRangedAnimationRoutine(delay = delay, duration = duration, rangeSubtype = unk))
            }
            0x77 -> {
                val unk = byteReader.next32() // 0, 1, 2...
                addEffectRoutine(FinishRangedAnimationRoutine(delay = delay, duration = duration, rangeSubtype = unk))
            }
            0x78 -> {
                expectZero32(byteReader)
                expectZero32(byteReader)
                expectZero32(byteReader)
                addEffectRoutine(DisplayDeadRoutine(delay = delay))
            }
            0x79 -> {
                val value = byteReader.next32()
                addEffectRoutine(AdjustAnimationModeRoutine(delay = delay, mode = 0, value = value))
            }
            0x7A -> {
                byteReader.nextFloat()
                byteReader.nextFloat()
                byteReader.nextFloat()
                byteReader.nextFloat()

                byteReader.nextFloat()
                byteReader.nextFloat()

                val joint = byteReader.next32()
                byteReader.nextFloat()

                expectZero32(byteReader, count = 2)

                addEffectRoutine(ActorJumpRoutine(delay = delay, duration = duration, targetJoint = joint))
            }
            0x84 -> {
                val inOutFlag = byteReader.next16()
                val index = byteReader.next16()
                addEffectRoutine(DualWieldEngageRoutine(delay = delay, inOutFlag = inOutFlag, index = index))
            }
            0x85 -> {
                // This is commonly used in the loop-start and loop-end routines, referring to the loop-routine
                // Since it's used in both, is it a toggle? Maybe a kill-switch?
                val loopId = byteReader.nextDatId()
                expectZero32(byteReader)
                addEffectRoutine(EndLoopRoutine(delay = delay, duration = duration, refId = loopId))
            }
            0x89 -> {
                expectZero32(byteReader)
                addEffectRoutine(DisplayRangedModelRoutine(delay = delay, duration = duration))
            }
            0x8C -> {
                val value = byteReader.next32()
                addEffectRoutine(AdjustAnimationModeRoutine(delay = delay, mode = 1, value = value))
            }
            0x98 -> {
                val value = byteReader.next32()
                addEffectRoutine(LoadBaseModelRoutine(delay = delay, modelId = value))
            }
            0xA3 -> {
                val hidden = byteReader.next32() == 1
                val slot = byteReader.next16()
                val unk = byteReader.next16()
                addEffectRoutine(ToggleModelVisibilityRoutine(delay = delay, hidden = hidden, slot = slot))
            }
            0xA4 -> {
                val value = byteReader.next32()
                addEffectRoutine(AdjustAnimationModeRoutine(delay = delay, mode = 2, value = value))
            }
            0xA5 -> {
                val value = byteReader.next32()
                addEffectRoutine(AdjustAnimationModeRoutine(delay = delay, mode = 3, value = value))
            }
            else -> {
                addEffectRoutine(NotImplementedRoutine(delay = delay, duration = duration))
                return false
            }
        }
        return true
    }

    private fun parseSection3(byteReader: ByteReader, opCode: Int, numArgs: Int, effectRoutineDefinition: EffectRoutineDefinition) : Boolean {
        val effect: OnCompleteEffect = when (opCode) {
            0x00 -> OnCompleteEffect {  }
            0x01 -> {
                effectRoutineDefinition.autoRunHeuristic = true
                OnCompleteEffect { it.onLoop() }
            }
            0x69, 0x6A -> return true // Probably used to clean up some state for conditionals?
            else -> return false
        }

        effectRoutineDefinition.completionEffects += effect
        return true
    }
    
    private fun addEffectRoutine(effect: Effect) {
        if (randomChildBlock != null) {
            randomChildBlock!!.children += effect
        } else {
            effectRoutineDefinition.effects.add(effect)
        }
    }

    private fun parseSoundEffectEmitter(byteReader: ByteReader, delay: Int, duration: Int, target: SoundEffectTarget): SoundEffectRoutine {
        val soundRef = byteReader.nextDatId()
        expectZero32(byteReader)
        byteReader.next32()
        val far = byteReader.nextFloat()
        val near = byteReader.nextFloat()
        val unk = byteReader.nextFloat()

        return SoundEffectRoutine(delay = delay, duration = duration, id = soundRef, farDistance = far, nearDistance = near, target = target)
    }

    private fun parseFlinchEffect(byteReader: ByteReader, delay: Int, useTarget: Boolean): FlinchRoutine {
        byteReader.nextFloat() // Usually 1.0 - multiplies the joint-transforms?
        byteReader.nextFloat() // Usually 1.0
        byteReader.next32()
        byteReader.nextFloat() // Usually 1.0
        val animationDuration = byteReader.nextFloat()
        byteReader.next32()
        byteReader.next32()
        return FlinchRoutine(delay = delay, animationDuration = animationDuration, useTarget = useTarget)
    }

}
