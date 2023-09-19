package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface TimeMessageADT

object GameEndEvent
