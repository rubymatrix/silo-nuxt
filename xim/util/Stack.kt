package xim.util

class Stack<T> {

    private val elements = ArrayList<T>()

    fun push(t: T) {
        elements.add(t)
    }

    operator fun plusAssign(t: T) {
        push(t)
    }

    operator fun plusAssign(t: List<T>) {
        if (t.isEmpty()) { return }
        elements.addAll(t.reversed())
    }

    fun pop(): T {
        return elements.removeLastOrNull() ?: throw IllegalStateException("Popping on empty")
    }

    fun peek(): T? {
        return elements.lastOrNull()
    }

    fun isEmpty(): Boolean {
        return elements.isEmpty()
    }

    fun clear() {
        elements.clear()
    }

}