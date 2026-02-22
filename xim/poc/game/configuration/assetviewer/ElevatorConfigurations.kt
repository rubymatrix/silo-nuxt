package xim.poc.game.configuration.assetviewer

import xim.poc.game.actor.components.ElevatorConfiguration
import xim.poc.game.actor.components.ElevatorStatus.IdleBottom
import xim.poc.game.actor.components.ElevatorStatus.IdleTop
import xim.resource.DatId
import kotlin.time.Duration.Companion.seconds

object ElevatorConfigurations {

    private val elevators = mapOf(
        // Pso'Xja
        9 to listOf(
            ElevatorConfiguration(
                elevatorId = DatId("@090"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9a"),
                bottomDoorIds = listOf(DatId("_i9b")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@091"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9c"),
                bottomDoorIds = listOf(DatId("_i9d")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@092"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9e"),
                bottomDoorIds = listOf(DatId("_i9f")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@093"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9g"),
                bottomDoorIds = listOf(DatId("_i9h")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@094"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9i"),
                bottomDoorIds = listOf(DatId("_i9j")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@095"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9k"),
                bottomDoorIds = listOf(DatId("_i9l")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@096"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9m"),
                bottomDoorIds = listOf(DatId("_i9n")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@097"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_i9o"),
                bottomDoorIds = listOf(DatId("_i9p"), DatId("_i9q")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@098"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_09j"),
                bottomDoorIds = listOf(DatId("_09k")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@099"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_09p"),
                bottomDoorIds = listOf(DatId("_09q")),
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@09a"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_09m"),
                bottomDoorIds = listOf(DatId("_09l")),
            ),
        ),

        // Fort Ghelsba
        141 to listOf(
            ElevatorConfiguration(
                elevatorId = DatId("@3x0"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_3x4"),
                bottomDoorIds = listOf(DatId("_3x3")),
                runningDoorIds = listOf(DatId("_3x0"), DatId("_3x5"), DatId("_3x6")),
                autoRunInterval = null,
            ),
        ),

        // Palborough Mines
        143 to listOf(
            ElevatorConfiguration(
                elevatorId = DatId("@3z0"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_3z5"),
                bottomDoorIds = listOf(DatId("_3z4")),
                autoRunInterval = null,
            ),
        ),

        // Davoi
        149 to listOf(
            ElevatorConfiguration(
                elevatorId = DatId("@450"),
                activeDuration = 4.seconds,
                topDoorId = DatId("_454"),
                bottomDoorIds = listOf(DatId("_453")),
                runningDoorIds = listOf(DatId("_450"), DatId("_455"), DatId("_456")),
                autoRunInterval = null,
            ),
        ),

        // Metalworks
        237 to listOf(
            ElevatorConfiguration(
                elevatorId = DatId("@6l0"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_6ls"),
                bottomDoorIds = listOf(DatId("_6lt")),
                runningDoorIds = listOf(DatId("_6lj")),
                baseStatus = IdleBottom,
            ),
            ElevatorConfiguration(
                elevatorId = DatId("@6l1"),
                activeDuration = 8.seconds,
                topDoorId = DatId("_6lu"),
                bottomDoorIds = listOf(DatId("_6lv")),
                runningDoorIds = listOf(DatId("_6lk")),
                baseStatus = IdleTop,
            ),
        )
    )

    fun get(zoneId: Int): List<ElevatorConfiguration> {
        return elevators[zoneId] ?: emptyList()
    }

}
