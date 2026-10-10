package io.github.sammynashed.emran.glance.state

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.glance.state.GlanceStateDefinition
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EmranWidgetStateDefinition : GlanceStateDefinition<EmranWidgetState> {

    private const val DATASTORE_FILE_PREFIX = "emran_widget_"
    private val mutex = Mutex()
    private val datastores = mutableMapOf<String, DataStore<EmranWidgetState>>()

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<EmranWidgetState> {
        return mutex.withLock {
            datastores.getOrPut(fileKey) {
                DataStoreFactory.create(
                    serializer = EmranWidgetStateSerializer,
                    produceFile = { context.dataStoreFile(DATASTORE_FILE_PREFIX + fileKey) }
                )
            }
        }
    }
    
    override fun getLocation(context: Context, fileKey: String): File {
        return context.dataStoreFile(DATASTORE_FILE_PREFIX + fileKey)
    }
}
