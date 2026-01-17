package com.esalinify.data

import androidx.annotation.DrawableRes
import com.esalinify.R

data class CommunicationOption(
    val id: Int,
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int
)

object CommunicationData {
    val options = listOf(
        CommunicationOption(
            id = 1,
            title = "Communicate through camera",
            description = "Uses camera to translate hand signs",
            imageRes = R.drawable.ic_camera_option
        ),
        CommunicationOption(
            id = 2,
            title = "Communicate through keyboard typing",
            description = "(Best for deaf user)",
            imageRes = R.drawable.ic_keyboard_option
        )
    )
}
