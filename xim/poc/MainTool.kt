package xim.poc

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.audio.AudioManager
import xim.poc.browser.*
import xim.poc.browser.ParserContext.Companion.staticResource
import xim.poc.camera.CameraReference
import xim.poc.camera.PolarCamera
import xim.poc.game.*
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.ZoneDefinitionManager
import xim.poc.gl.Drawer
import xim.poc.gl.FrameBufferManager
import xim.poc.gl.OcclusionManager
import xim.poc.gl.UiElementBuffer
import xim.poc.tools.*
import xim.poc.ui.*
import xim.resource.*
import xim.resource.table.*
import xim.util.Fps.millisToFrames
import xim.util.OnceLogger
import xim.util.OncePerFrame
import xim.util.Timer
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

object GlobalDirectory {
    lateinit var directoryResource: DirectoryResource
    lateinit var shadowResource: DirectoryResource
    val shadowTexture by lazy { TextureLink("system  kage_tex", shadowResource) }
}

object MainTool {

    lateinit var platformDependencies: PlatformDependencies
    lateinit var drawer: Drawer

    private var resourceDependenciesAreLoaded = false
    private var setupZone = false
    private var welcomed = false

    fun run(platformDependencies: PlatformDependencies) {
        this.platformDependencies = platformDependencies

        val viewExecutor = platformDependencies.viewExecutor
        drawer = platformDependencies.drawer

        CameraReference.setInstance(PolarCamera(Vector3f(), Vector3f(0f, -1.5f, 0f), MainTool.platformDependencies.screenSettingsSupplier))

        viewExecutor.beginDrawing {
            try {
                Timer.time("Loop") { loop(it) }
                Timer.report()
            } catch (t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }
    }

    private fun loop(elapsedTimeInMs: Double) {
        platformDependencies.keyboard.poll()
        DatLoader.update(elapsedTimeInMs.milliseconds)

        val currentLogicalFrameIncrement = millisToFrames(elapsedTimeInMs)
        val throttledFrameIncrement = min(3f, currentLogicalFrameIncrement.toFloat())

        val gameFrameIncrement = if (isCheckBox("pause")) {
            0f
        } else {
            if (!isCheckBox("todOverride")) { EnvironmentManager.advanceTime(currentLogicalFrameIncrement) }
            throttledFrameIncrement * GameState.gameSpeed
        }

        OncePerFrame.onNewFrame()
        internalLoop(gameFrameIncrement, throttledFrameIncrement)
    }

    fun internalLoop(elapsedFrames: Float, trueElapsedFrames: Float) {
        UiElementBuffer.reset()

        if (!resourceDependenciesLoaded()) {
            return drawDownloadingScreenCover(elapsedFrames)
        }

        refreshCurrentSceneIfNeeded()
        if (!SceneManager.isFullyLoaded()) {
            return drawDownloadingScreenCover(elapsedFrames)
        }

        // "Game" logic
        setupZoneGameLogicIfNeeded()

        Timer.time("gameLogic") { GameEngine.tick(elapsedFrames) }

        // Update entities
        val currentScene = SceneManager.getCurrentScene()
        currentScene.update(elapsedFrames)

        if (!ZoneChanger.isChangingZones()) {
            val zoneInteraction = Timer.time("checkInteractions") { currentScene.checkInteractions() }
            if (zoneInteraction != null) { ZoneChanger.beginChangeZone(zoneInteraction) }
        }

        handleKeyEvents()
        ClickHandler.handleClickEvents()
        PlayerTargetSelector.updateTarget(elapsedFrames)

        ActorManager.updateAll(elapsedFrames)
        CameraReference.getInstance().update(platformDependencies.keyboard, trueElapsedFrames)

        val playerActor = ActorStateManager.player()
        val playerActorCollision = playerActor.lastCollisionResult
        val zoneObjectsByArea = Timer.time("cullZoneObjects") { currentScene.getVisibleZoneObjects(CameraReference.getInstance(), ZoneObjectCullContext) }

        OcclusionManager.update(elapsedFrames)
        Timer.time("updateEffects") { EffectManager.update(elapsedFrames) }
        val lightingParticles = EffectManager.getLightingParticles()

        EnvironmentManager.update(elapsedFrames)

        val (playerEnvironmentArea, playerEnvironmentId) = playerActorCollision.getEnvironment()
        EnvironmentManager.updateWeatherAudio(currentScene.getMainArea(), playerEnvironmentId)
        val drawDistance = EnvironmentManager.getDrawDistance(playerEnvironmentArea, playerEnvironmentId)

        UiStateHelper.update(trueElapsedFrames)

        Timer.time("updateAudio") { AudioManager.update(elapsedFrames) }

        AoeIndicators.update()

        // Begin 3D effects
        FrameBufferManager.releaseClaimedBuffers()
        FrameBufferManager.bindAndClearScreenBuffer()

        // The sky
        drawer.setupXim(CameraReference.getInstance())
        EnvironmentManager.drawSkyBox(drawer)

        // The zone
        Timer.time("drawZone") { zoneObjectsByArea.forEach { ZoneDrawer.drawZoneObjects(it.key, it.value, drawDistance, CameraReference.getInstance(), lightingParticles) } }

        // Actor models
        ActorDrawer.update(elapsedFrames)

        // Opaque Actors
        drawer.setupXimSkinned(CameraReference.getInstance())
        for (actor in ActorManager.getVisibleActors()) {
            if (actor.isTranslucent()) { continue }

            val hasDebugOverride = ZoneObjectTool.drawActor(actor)
            if (hasDebugOverride) { continue }

            Timer.time("drawActor") { ActorDrawer.drawActor(actor, lightingParticles) }
            Timer.time("emitFootSteps") { ZoneDrawer.emitFootSteps(actor, GlobalDirectory.directoryResource, currentScene.getMainAreaRootDirectory()) }
        }

        Timer.time("drawShadows") { ZoneDrawer.drawActorShadows(drawDistance) }

        // Weapon-trace Effects
        WeaponTraceDrawer.draw()

        // Particles & Effects
        val postEffects = Timer.time("drawParticles") { ParticleDrawer.drawAllParticlesAndGetDecals(lightingParticles) }

        Timer.time("drawDecal") { ZoneDrawer.drawDecalEffects(postEffects.decalEffects, drawDistance) }
        Timer.time("drawLensFlare") { ZoneDrawer.drawLensFlares(postEffects.lensFlareEffects) }

        // Translucent Actors
        drawer.setupXimSkinned(CameraReference.getInstance())
        for (actor in ActorManager.getVisibleActors()) {
            if (!actor.isTranslucent()) { continue }
            Timer.time("drawActorT") { ActorDrawer.drawActor(actor, lightingParticles) }
        }

        // 3D Debug tool
        Timer.time("debugTools3d") {
            LineDrawingTool.renderLines(drawer)
            BoxDrawingTool.render(drawer)
            SphereDrawingTool.render(drawer)
        }

        // 3D effects are done, so output the screen-buffer
        FrameBufferManager.unbind()
        drawer.drawScreenBuffer(FrameBufferManager.getCurrentScreenBuffer())

        // 2D & UI
        drawer.setupXimUi()
        ScreenFlasher.draw()

        if (GameState.gameSpeed == 0f) {
            UiElementHelper.drawBlackScreenCover(0.3f)
        } else if (GameState.gameSpeed < 1f) {
            UiElementHelper.drawBlackScreenCover(0.1f)
        }

        Timer.time("updateUi") { UiElementHelper.update(trueElapsedFrames) }

        MapDrawer.draw(playerActorCollision)

        if (!isCheckBox("UiDisabled")) {
            Timer.time("drawUi") {
                DamageTextManager.draw(elapsedFrames)
                PartyUi.draw()
                TargetInfoUi.draw()

                UiElementTool.drawPreviewElement()

                UiStateHelper.draw()
                CastTimeUi.draw()

                GameState.getGameMode().drawUi()
                UiElementHelper.drawEnqueuedCommands()
            }
        }

        ScreenFader.update(elapsedFrames)
        ScreenFader.draw()

        // 2D & misc Debug tools
        Timer.time("debugTools") {
            PlayerPositionTracker.update()
            PlayerAnimationTrackerTool.update()
            EnvironmentTool.update()
            AudioVolumeTool.update()
            RoutineViewer.update()
            TexturePreviewer.draw()
            ParticleGenTool.update()
            PlayerLookTool.update()
            LocalStorage.persistPlayerState()
            BattleLocationTool.update()
            UiPositionTool.update()
            HelpWindowUi.draw()
            BumpMapLinks.update()
        }

        drawer.finish()
    }

    private fun resourceDependenciesLoaded(): Boolean {
        if (resourceDependenciesAreLoaded) { return true }
        LoadingElement.show()

        if (!ExecutionEnvironment.isReady() || !DatLoader.isReady()) { return false }

        val systemEffects = DatLoader.load("ROM/0/0.DAT", parserContext = staticResource)
        FontMojiHelper.register()

        val tables = listOf(
            MainDll,
            FileTableManager,
            ItemModelTable,
            SpellAnimationTable,
            SpellNameTable,
            SpellInfoTable,
            ZoneNameTable,
            ZoneSettingsTable,
            AbilityInfoTable,
            AbilityNameTable,
            AbilityDescriptionTable,
            AbilityTable,
            StatusEffectNameTable,
            StatusEffectIcons,
            NpcTable,
            MountNameTable,
            ItemAnimationTable,
            MobSkillNameTable,
            MobSkillInfoTable,
            InventoryItems,
            AugmentTable,
            KeyItemTable,
        )

        tables.forEach { it.preload() }
        if (tables.any { !it.isFullyLoaded() } || !systemEffects.isReady()) { return false }

        GlobalDirectory.directoryResource = systemEffects.getAsResource()
        UiResourceManager.prefetch()
        PcModelLoader.preload()
        FrameBufferManager.setup()
        if (!FrameBufferManager.isReady() || !PcModelLoader.isFullyLoaded()) { return false }

        GameEngine.setup()
        AudioManager.preloadSystemSoundEffects()

        resourceDependenciesAreLoaded = true
        LoadingElement.hide()

        DatLoader.load("ROM/27/81.DAT", parserContext = staticResource).onReady { GlobalDirectory.shadowResource = it.getAsResource() }
        return true
    }

    private fun refreshCurrentSceneIfNeeded() {
        val player = ActorStateManager.player()

        val playerZoneSettings = player.zone ?: throw IllegalStateException("Player starting position was not configured")

        val playerZoneConfig = ZoneConfig(
            zoneId = playerZoneSettings.zoneId,
            startPosition = Vector3f(player.position),
            entryId = playerZoneSettings.entryId,
            customDefinition = ZoneDefinitionManager[playerZoneSettings.zoneId],
            mogHouseSetting = playerZoneSettings.mogHouseSetting
        )

        val currentScene = SceneManager.getNullableCurrentScene()
        if (!SceneManager.isReloadRequested() && playerZoneConfig.matches(currentScene?.config)) { return }

        if (currentScene != null) { GameState.getGameMode().onUnloadZone(currentScene.config) }

        SceneManager.unloadScene()
        setupZone = false

        ActorStateManager.clear()
        ActorManager.clear()

        MapDrawer.clear()
        FrameCoherence.clear()
        EffectManager.clearAllEffects()
        ParticleGenTool.clear()
        OcclusionManager.clear()

        OnceLogger.clear()
        DatLoader.releaseAll()

        SceneManager.loadScene(playerZoneConfig, playerZoneSettings.subAreaId)
    }

    private fun drawDownloadingScreenCover(elapsedFrames: Float) {
        drawer.setupXimUi()
        UiElementHelper.drawBlackScreenCover(opacity = 1f)
        UiElementHelper.update(elapsedFrames)
        UiElementHelper.drawDownloadingDataElement(Vector2f(620f, 550f).scale(UiElementHelper.offsetScaling))
    }

    private fun setupZoneGameLogicIfNeeded() {
        if (setupZone) { return }
        setupZone = true

        emitWelcomeMessage()

        val playerActor = ActorManager.player()
        CameraReference.getInstance().setTarget(playerActor.displayPosition)

        ZoneChanger.onZoneIn()
        EnvironmentManager.setScene(SceneManager.getCurrentScene())

        val zoneConfig = SceneManager.getCurrentScene().config
        GameState.getGameMode().onChangedZones(zoneConfig)

        setupDebugTools()
    }

    private fun emitWelcomeMessage() {
        if (welcomed) { return }
        welcomed = true
        ChatLog.addLine("<<< Welcome to Xim! Client version: June 2024 >>>", ChatLogColor.SystemMessage)
    }

    private fun setupDebugTools() {
        FrameTool.setup()
        ScreenSettingsTool.setup()
        AudioVolumeTool.setup()
        EnvironmentTool.setup()
        ZoneObjectTool.setup(SceneManager.getCurrentScene().getMainAreaRootDirectory())
        ZoneNpcTool.setup()
        NpcSpawningTool.setup()
        FurnitureSpawningTool.setup()
        SpellHelper.setup()
        PlayerCustomizer.setup()
        InventoryTool.setup()
        UiElementTool.setup()
        ZoneChangeTool.setup()
        BattleLocationTool.setup()
        KeybindTool.setup()
        UiPositionTool.setup()
    }

    private fun handleKeyEvents() {
        if (ZoneChanger.isChangingZones() || EventScriptRunner.isRunningScript()) { return }
        val player = ActorStateManager.player()
        if (player.isFishing()) { return }

        if (player.targetState.targetId != null && platformDependencies.keyboard.isKeyPressed(GameKey.TargetLock)) {
            GameClient.toggleTargetLock(player.id)
            return
        }

        if (player.isEngaged() && platformDependencies.keyboard.isKeyPressed(GameKey.Disengage)) {
            GameClient.submitPlayerDisengage()
            return
        }

        if ((player.isIdle() || player.isResting()) && platformDependencies.keyboard.isKeyPressed(GameKey.Rest)) {
            GameClient.toggleResting(player.id)
            return
        }
    }

}
