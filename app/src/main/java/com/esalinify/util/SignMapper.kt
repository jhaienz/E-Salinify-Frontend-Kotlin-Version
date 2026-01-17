package com.esalinify.util

import com.esalinify.R

object SignMapper {
    private val signDrawables = mapOf(
        'a' to R.drawable.sign_a,
        'b' to R.drawable.sign_b,
        'c' to R.drawable.sign_c,
        'd' to R.drawable.sign_d,
        'e' to R.drawable.sign_e,
        'f' to R.drawable.sign_f,
        'g' to R.drawable.sign_g,
        'h' to R.drawable.sign_h,
        'i' to R.drawable.sign_i,
        'j' to R.drawable.sign_j,
        'k' to R.drawable.sign_k,
        'l' to R.drawable.sign_l,
        'm' to R.drawable.sign_m,
        'n' to R.drawable.sign_n,
        'o' to R.drawable.sign_o,
        'p' to R.drawable.sign_p,
        'q' to R.drawable.sign_q,
        'r' to R.drawable.sign_r,
        's' to R.drawable.sign_s,
        't' to R.drawable.sign_t,
        'u' to R.drawable.sign_u,
        'v' to R.drawable.sign_v,
        'w' to R.drawable.sign_w,
        'x' to R.drawable.sign_x,
        'y' to R.drawable.sign_y,
        'z' to R.drawable.sign_z
    )

    fun getSignDrawable(char: Char): Int? {
        return signDrawables[char.lowercaseChar()]
    }

    fun isSpace(char: Char): Boolean = char == ' '

    fun isSupported(char: Char): Boolean {
        return char == ' ' || signDrawables.containsKey(char.lowercaseChar())
    }

    fun normalizeToSignKey(ch: Char): Char? {
        val key = ch.lowercaseChar()
        return when {
            signDrawables.containsKey(key) -> key
            ch == ' ' -> ' '
            else -> null
        }
    }
}
