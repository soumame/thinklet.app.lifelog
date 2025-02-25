package ai.fd.thinklet.library.lifelog.data.snapshot.impl

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class CustomLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = lifecycleRegistry

    private fun update(state: Lifecycle.State) {
        CoroutineScope(Dispatchers.Main).launch {
            lifecycleRegistry.currentState = state
        }
    }

    fun onStart() {
        update(Lifecycle.State.STARTED)
    }

    fun onStop() {
        update(Lifecycle.State.CREATED)
    }
}
