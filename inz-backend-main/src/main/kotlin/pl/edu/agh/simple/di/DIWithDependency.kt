package pl.edu.agh.simple.di

import kotlinx.serialization.builtins.serializer

class DIWithDependency(val first: DIInterface) {
    init {
        first.printJson(1, Int.serializer())
    }
}