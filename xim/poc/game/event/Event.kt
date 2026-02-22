package xim.poc.game.event

interface Event {
    fun apply(): List<Event>
}