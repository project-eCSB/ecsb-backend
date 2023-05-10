package pl.edu.agh.auth.domain

enum class Token(val suffix: String) {
    LOGIN_USER("loginuser"),
    GAME_TOKEN("gametoken")
}