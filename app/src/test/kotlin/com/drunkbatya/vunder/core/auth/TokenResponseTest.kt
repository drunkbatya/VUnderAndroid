package com.drunkbatya.vunder.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenResponseTest {
    private fun parse(body: String) = TokenResponse.from(Json.parseToJsonElement(body).jsonObject)

    @Test
    fun challengesAreRecognisedInBothErrorShapes() {
        assertEquals(TokenResponse.Processing, parse("""{"processing":1,"access_token":"t","user_id":1}"""))
        assertEquals(
            TokenResponse.Captcha("42", "https://api.vk.ru/captcha.php?sid=42"),
            parse("""{"error":"need_captcha","captcha_sid":"42","captcha_img":"https://api.vk.ru/captcha.php?sid=42"}"""),
        )
        assertEquals(
            TokenResponse.Validation("https://id.vk.ru/validate"),
            parse("""{"error":{"error_code":17,"redirect_uri":"https://id.vk.ru/validate"}}"""),
        )
        assertEquals(
            TokenResponse.Issued("t", 7, "h"),
            parse("""{"access_token":"t","user_id":"7","trusted_hash":"h"}"""),
        )
    }

    @Test
    fun failureMessageCarriesErrorNameAndDescription() {
        assertEquals(
            TokenResponse.Failure("invalid_client: Username or password is incorrect"),
            parse("""{"error":"invalid_client","error_description":"Username or password is incorrect"}"""),
        )
        assertEquals(TokenResponse.Failure("error: Access denied"), parse("""{"error":{"error_code":15,"error_msg":"Access denied"}}"""))
        assertEquals(TokenResponse.Failure("need_captcha"), parse("""{"error":"need_captcha"}"""))
    }
}
