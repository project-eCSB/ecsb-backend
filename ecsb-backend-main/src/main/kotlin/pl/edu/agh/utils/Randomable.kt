package pl.edu.agh.utils

interface Randomable<T : Comparable<T>> {
    fun nextRandomInRange(range: ClosedRange<T>): T
}
