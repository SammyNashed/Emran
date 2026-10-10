package io.github.sammynashed.emran.glance.state

import androidx.datastore.core.Serializer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for saving and loading EmranWidgetState instances to/from Jetpack DataStore.
 * We use Moshi for JSON serialization which makes schema versioning and debugging easy.
 */
object EmranWidgetStateSerializer : Serializer<EmranWidgetState> {
    override val defaultValue: EmranWidgetState = EmranWidgetState()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(EmranWidgetState::class.java)

    override suspend fun readFrom(input: InputStream): EmranWidgetState {
        return try {
            val jsonString = input.readBytes().decodeToString()
            adapter.fromJson(jsonString) ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: EmranWidgetState, output: OutputStream) {
        val jsonString = adapter.toJson(t)
        output.write(jsonString.encodeToByteArray())
    }
}
