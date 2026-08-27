package app.readbound.ui

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import androidx.compose.ui.platform.LocalContext

@Composable
fun ReaderIcon(
    @RawRes resource: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: ColorFilter? = null,
) {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    AsyncImage(
        model = resource,
        imageLoader = loader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = tint,
    )
}
