package com.fgteam.paymentobserver.auth

data class AdminSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: String,
    val refreshTokenExpiresIn: String,
    val accessTokenExpiresInUnix: Long,
    val refreshTokenExpiresInUnix: Long,
    val fullName: String,
    val role: String
)

class AuthenticationRequiredException(message: String = "Sesi admin diperlukan") : Exception(message)
class ObserverAccessDeniedException : Exception("Akun tidak memiliki akses Payment Observer")
class ApiException(val statusCode: Int, message: String) : Exception(message)
