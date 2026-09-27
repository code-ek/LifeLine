package com.lifeline.app.map

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lifeline.app.emergency.SosAlert
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Imperative handle the screen uses to move the camera and read the visible area. */
class MapController {
    internal var map: MapLibreMap? = null

    fun visibleBounds(): GeoBounds? {
        val b = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return GeoBounds(north = b.getLatNorth(), south = b.getLatSouth(), east = b.getLonEast(), west = b.getLonWest())
    }

    fun zoom(): Double = map?.cameraPosition?.zoom ?: 0.0

    fun centerOn(lat: Double, lon: Double, zoom: Double = 15.0) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }

    fun showBounds(bounds: GeoBounds) {
        map?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west), 48
            )
        )
    }
}

private const val SOS_SOURCE = "sos-alerts"
private const val ME_SOURCE = "my-location"

/**
 * MapLibre vector map. Renders from downloaded offline regions when there is no internet.
 * SOS alerts are red circles; this phone is a blue dot.
 */
@Composable
fun OfflineMapView(
    modifier: Modifier,
    controller: MapController,
    alerts: List<SosAlert>,
    myLocation: Location?,
    onStyleFailed: () -> Unit,
    onStyleLoaded: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { onStyleFailed() }
            getMapAsync { map ->
                controller.map = map
                map.uiSettings.isRotateGesturesEnabled = false
                map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loaded ->
                    loaded.addSource(GeoJsonSource(ME_SOURCE))
                    loaded.addSource(GeoJsonSource(SOS_SOURCE))
                    loaded.addLayer(
                        CircleLayer("sos-halo", SOS_SOURCE).withProperties(
                            circleRadius(18f), circleColor("#D70015"), circleOpacity(0.25f)
                        )
                    )
                    loaded.addLayer(
                        CircleLayer("sos-dot", SOS_SOURCE).withProperties(
                            circleRadius(8f), circleColor("#D70015"),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)
                        )
                    )
                    loaded.addLayer(
                        CircleLayer("me-dot", ME_SOURCE).withProperties(
                            circleRadius(7f), circleColor("#0A84FF"),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)
                        )
                    )
                    style = loaded
                    onStyleLoaded()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            controller.map = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(style, alerts) {
        val source = style?.getSourceAs<GeoJsonSource>(SOS_SOURCE) ?: return@LaunchedEffect
        val features = alerts.mapNotNull { alert ->
            val loc = alert.payload.location ?: return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude)).apply {
                addStringProperty("type", alert.payload.type.label)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, myLocation) {
        val source = style?.getSourceAs<GeoJsonSource>(ME_SOURCE) ?: return@LaunchedEffect
        val me = myLocation ?: return@LaunchedEffect
        source.setGeoJson(Point.fromLngLat(me.longitude, me.latitude))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
