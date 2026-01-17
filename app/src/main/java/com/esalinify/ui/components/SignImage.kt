package com.esalinify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.esalinify.R
import com.esalinify.util.SignMapper

@Composable
fun SignImage(
    char: Char,
    modifier: Modifier = Modifier
) {
    val signDrawable = SignMapper.getSignDrawable(char)

    when {
        SignMapper.isSpace(char) -> {
            // Render space as a small separator
            Spacer(modifier = Modifier.width(20.dp))
        }
        signDrawable != null -> {
            // Render the sign image
            Image(
                painter = painterResource(id = signDrawable),
                contentDescription = stringResource(R.string.sign_image_description, char),
                modifier = modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        else -> {
            // Render unsupported character placeholder
            Box(
                modifier = modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
