package pl.edu.agh.auth.domain

sealed class Token(val suffix: String) {
    object LOGIN_USER_TOKEN : Token("jwt")
    object GAME_TOKEN : Token("gametoken")
}
