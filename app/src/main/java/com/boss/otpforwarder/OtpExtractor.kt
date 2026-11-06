package com.boss.otpforwarder

/** 메시지 본문에서 인증번호로 보이는 숫자를 추출한다. */
object OtpExtractor {

    // "인증", "verification", "code", "OTP", "PIN" 등 OTP 맥락 키워드
    private val contextKeywords = listOf(
        "인증", "인증번호", "확인번호", "보안", "코드",
        "otp", "code", "verification", "verify", "pin", "one-time", "passcode"
    )

    // 3~8자리 숫자(하이픈/공백 허용). 전화번호 등 긴 숫자는 제외.
    private val codeRegex = Regex("""(?<!\d)(\d[\d\- ]{2,8}\d)(?!\d)""")

    data class Result(val isOtp: Boolean, val code: String?)

    fun analyze(text: String?): Result {
        if (text.isNullOrBlank()) return Result(false, null)
        val lower = text.lowercase()
        val hasKeyword = contextKeywords.any { lower.contains(it) }

        val candidates = codeRegex.findAll(text)
            .map { it.value.replace(Regex("""[\- ]"""), "") }
            .filter { it.length in 4..8 }
            .toList()

        val code = candidates.firstOrNull()
        // 키워드가 있고 숫자 후보가 있으면 OTP로 판단
        val isOtp = hasKeyword && code != null
        return Result(isOtp, if (isOtp) code else null)
    }
}
